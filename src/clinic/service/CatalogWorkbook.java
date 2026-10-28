package clinic.service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** JDK-only XLSX reader with header mapping and external XML entities disabled. */
final class CatalogWorkbook {
    private static final String NS="http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private CatalogWorkbook() {}
    static MedicineCatalog read(Path path)throws IOException {
        Map<String,byte[]> files=new HashMap<>();
        try(ZipInputStream zip=new ZipInputStream(Files.newInputStream(path))) {
            for(ZipEntry entry;(entry=zip.getNextEntry())!=null;) {
                if(entry.getName().startsWith("xl/"))files.put(entry.getName(),zip.readAllBytes());
            }
        }
        try {
            List<String> strings=new ArrayList<>();
            if(files.containsKey("xl/sharedStrings.xml")) {
                NodeList items=xml(files.get("xl/sharedStrings.xml")).getElementsByTagNameNS(NS,"si");
                for(int i=0;i<items.getLength();i++)strings.add(text((Element)items.item(i)));
            }
            Document book=xml(required(files,"xl/workbook.xml"));
            Element sheet=(Element)book.getElementsByTagNameNS(NS,"sheet").item(0);
            String relation=sheet.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships","id");
            NodeList links=xml(required(files,"xl/_rels/workbook.xml.rels")).getElementsByTagNameNS("*","Relationship");
            String target=null;
            for(int i=0;i<links.getLength();i++) {
                Element link=(Element)links.item(i);
                if(link.getAttribute("Id").equals(relation))target=link.getAttribute("Target");
            }
            if(target==null)throw new IOException("First worksheet relationship is missing.");
            String worksheet=target.startsWith("/")?target.substring(1):Path.of("xl").resolve(target).normalize().toString();
            NodeList rows=xml(required(files,worksheet)).getElementsByTagNameNS(NS,"row");
            if(rows.getLength()==0)throw new IOException("Medicine workbook has no header row.");
            Map<String,String> headers=cells((Element)rows.item(0),strings);
            Map<String,String> columns=new HashMap<>();headers.forEach((column,name)->columns.put(name.trim(),column));
            for(String header:List.of("Medicine","ATC code primary","ATC code secondary",
                    "WHO Model List of Essential Medications","Philippines"))
                if(!columns.containsKey(header))throw new IOException("Missing medicine workbook header: "+header);
            List<MedicineCatalog.Entry> entries=new ArrayList<>();
            for(int i=1;i<rows.getLength();i++) {
                Map<String,String> values=cells((Element)rows.item(i),strings);
                String name=get(values,columns,"Medicine");if(name.isBlank())continue;
                entries.add(new MedicineCatalog.Entry(name,get(values,columns,"ATC code primary"),
                    get(values,columns,"ATC code secondary"),flag(get(values,columns,"WHO Model List of Essential Medications")),
                    flag(get(values,columns,"Philippines"))));
            }
            return new MedicineCatalog(entries);
        }catch(IOException failure){throw failure;}
        catch(Exception failure){throw new IOException("Unable to parse medicine workbook.",failure);}
    }
    private static byte[] required(Map<String,byte[]> files,String name)throws IOException {
        byte[] data=files.get(name);if(data==null)throw new IOException("Missing XLSX part: "+name);return data;
    }
    private static Document xml(byte[] bytes)throws Exception {
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }
    private static String text(Element element) {
        NodeList parts=element.getElementsByTagNameNS(NS,"t");StringBuilder value=new StringBuilder();
        for(int i=0;i<parts.getLength();i++)value.append(parts.item(i).getTextContent());return value.toString();
    }
    private static Map<String,String> cells(Element row,List<String> strings) {
        Map<String,String> values=new HashMap<>();NodeList cells=row.getElementsByTagNameNS(NS,"c");
        for(int i=0;i<cells.getLength();i++) {
            Element cell=(Element)cells.item(i);NodeList nodes=cell.getElementsByTagNameNS(NS,"v");
            String value=nodes.getLength()==0?"":nodes.item(0).getTextContent();
            if(cell.getAttribute("t").equals("s"))value=strings.get(Integer.parseInt(value));
            if(cell.getAttribute("t").equals("inlineStr"))value=text(cell);
            values.put(cell.getAttribute("r").replaceAll("[0-9]",""),value.trim());
        }
        return values;
    }
    private static String get(Map<String,String> values,Map<String,String> columns,String header) {
        return values.getOrDefault(columns.get(header),"").trim();
    }
    private static boolean flag(String value)throws IOException {
        if(value.equals("1")||value.equals("1.0"))return true;
        if(value.equals("0")||value.equals("0.0")||value.isBlank())return false;
        throw new IOException("Unexpected medicine availability flag: "+value);
    }
}
