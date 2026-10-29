import clinic.service.*;
import clinic.model.*;
import clinic.app.*;
import clinic.app.ui.common.MedicinePicker;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.swing.*;

/** Tiny generated XLSX exercises real parser, ranking, selection and persistence. */
public final class MedicineCatalogChecks {
    private static int checks;
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    public static void main(String[] args)throws Exception {
        Path workbook=Files.createTempFile("medicine-fixture-",".xlsx");
        try {
            fixture(workbook);
            MedicineCatalog catalog=MedicineCatalog.read(workbook);
            check(catalog.entries().size()==4,"headers mapped by name, blank rows skipped, names deduplicated");
            check(catalog.search("alpha").stream().map(MedicineCatalog.Entry::name).toList()
                .equals(List.of("Alpha available","Alpha absent","Contains alpha")),"prefix before contains; Philippines first within rank");
            check(catalog.search("b02").get(0).name().equals("Beta"),"case-insensitive ATC search");
            Path invalid=Files.createTempFile("missing-header-",".xlsx");
            try {
                try(ZipInputStream input=new ZipInputStream(Files.newInputStream(workbook));
                        ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(invalid))) {
                    for(ZipEntry entry;(entry=input.getNextEntry())!=null;) {
                        byte[] bytes=input.readAllBytes();
                        String text=new String(bytes,StandardCharsets.UTF_8).replace("Philippines","Missing country");
                        part(output,entry.getName(),text);
                    }
                }
                try{MedicineCatalog.read(invalid);throw new AssertionError("Missing header accepted");}
                catch(java.io.IOException expected){check(expected.getMessage().contains("Philippines"),"missing required header fails clearly");}
            }finally{Files.deleteIfExists(invalid);}
            check(catalog.search("ALPHA AVAILABLE").get(0).whoEssential(),"WHO numeric flag mapped");
            check(!catalog.search("alpha absent").get(0).availableInPhilippines(),"Philippines zero mapped to false");
            MedicineCatalog strengthCatalog=new MedicineCatalog(List.of(
                new MedicineCatalog.Entry("Amoxicillin","J01CA04","",false,true),
                new MedicineCatalog.Entry("Paracetamol","N02BE01","",false,true),
                new MedicineCatalog.Entry("Antacid Liquid","A02AD01","",false,true)));
            check(strengthCatalog.findByName("Amoxicillin 500mg").orElseThrow().atcPrimary().equals("J01CA04"),
                "legacy amoxicillin strength matches the catalog entry");
            check(strengthCatalog.findByName("paracetamol 500mg").isPresent(),
                "legacy medicine strength matching is case-insensitive");
            check(strengthCatalog.findByName("Antacid Liquid 120ml").isPresent(),
                "legacy liquid volume matches the catalog entry");
            check(strengthCatalog.findByName("Unknown 50%").isEmpty(),
                "unmatched legacy name remains without a catalog result");
            List<MedicineCatalog.Entry> many=new ArrayList<>();
            for(int i=0;i<70;i++)many.add(new MedicineCatalog.Entry("Drug "+i,"","",false,false));
            check(new MedicineCatalog(many).search("").size()==50,"search capped at fifty");
            try(ApplicationContext app=new ApplicationContext(true)) {
                app.login.login("N001","nurse123").orElseThrow();
                SwingUtilities.invokeAndWait(()->{
                    Theme.install();MedicinePicker picker=new MedicinePicker(app,()->{},true);
                    JComboBox<?> combo=find(picker,JComboBox.class);
                    ((JTextField)combo.getEditor().getEditorComponent()).setText("Unknown fictional medicine");
                    check(!picker.validateForm()&&!picker.isFormReady()&&picker.formValue().isEmpty(),"picker rejects unknown free text inline");
                    JTextField atc=findAtc(picker);
                    check(atc!=null&&!atc.isEditable(),"shared picker ATC field is read-only");
                    MedicineCatalog.Entry chosen=catalog.search("Alpha available").get(0);
                    combo.setSelectedItem(new Medicine(chosen.product(),1,java.time.LocalDate.MAX,java.time.LocalDate.now(),""));
                    check(picker.isFormReady()&&atc.getText().equals("A01 / A02"),"catalog selection shows both ATC codes");
                    picker.removeNotify();
                });
            }
            matching();persistence();
            System.out.println("PASS: "+checks+" medicine catalog checks.");
        }finally{Files.deleteIfExists(workbook);}
    }
    private static void matching() {
        var expiry=java.time.LocalDate.now().plusDays(30);
        Medicine first=new Medicine(new MedicineProduct(" Sample ","A01",""),2,expiry,java.time.LocalDate.now(),"");
        Medicine second=new Medicine(new MedicineProduct("sample","B02",""),3,expiry,java.time.LocalDate.now(),"");
        check(BatchMatcher.find(List.of(first,second),new MedicineProduct("SAMPLE","C03",""),expiry).orElseThrow().count()==2,
            "duplicate matching uses normalized name only");
        check(new MedicineProduct("Paracetamol 500mg","","").displayName().equals("Paracetamol 500mg"),"legacy names retain embedded dosage text");
    }
    private static void persistence()throws Exception {
        Path folder=Files.createTempDirectory("catalog-persistence-");Path database=folder.resolve("clinic");
        try(ApplicationContext app=new ApplicationContext(database)) {
            app.login.bootstrapAdministrator("root","Admin","test-password");app.login.login("root","test-password").orElseThrow();
            SchoolNurse nurse=new SchoolNurse("catalog-nurse","Nurse");nurse.setCampusId("uic-main");
            app.admin.addUser(nurse,"test-password");
        }
        // Repository-level tests avoid any dependency on the production workbook.
        try(clinic.persistence.Database db=new clinic.persistence.Database(database)) {
            ClinicData data=clinic.persistence.ClinicRepository.create(db);
            MedicineProduct product=new MedicineProduct("Fixture medicine","A01","A02");
            data.addMedicine(product,12,java.time.LocalDate.now().plusDays(40),"");
            check(data.dispensableMedicinePage(0,25,"Fixture").rows().get(0).getProduct().equals(product),"ATC codes persist with stock");
            data.registerPatient(new Student("catalog-patient","Test","contact","CS",1,"A"));
            data.dispenseMedicineWithCampus("catalog-patient",product,1,"catalog-nurse","","uic-main");
            check(data.transactionPage(0,25,"Fixture",null,null).rows().get(0).getMedicineProduct().equals(product),
                "both ATC codes persist with transaction");
            data.addMedicine("Paracetamol 500mg",3,java.time.LocalDate.now().plusDays(50),"");
            check(data.dispensableMedicinePage(0,25,"Paracetamol").rows().get(0).getProduct().atcPrimary().isEmpty(),"legacy name-only rows load with blank ATC");
            db.transaction(c->{try(var statement=c.createStatement()) {
                statement.executeUpdate("UPDATE medicine_batches SET strength='500mg',formulation='tablet' WHERE name='Paracetamol 500mg'");
            }return null;});
            Medicine legacy=data.dispensableMedicinePage(0,25,"Paracetamol").rows().get(0);
            check(legacy.getName().equals("Paracetamol 500mg")&&legacy.getProduct().atcLabel().isBlank(),
                "legacy storage fields are not shown or misread as ATC");
            data.dispenseMedicineWithCampus("catalog-patient",legacy.getProduct(),1,"catalog-nurse","","uic-main");
            db.transaction(c->{try(var statement=c.createStatement();var rows=statement.executeQuery(
                    "SELECT strength,formulation FROM medicine_batches WHERE name='Paracetamol 500mg'")) {
                rows.next();check(rows.getString(1).equals("500mg")&&rows.getString(2).equals("tablet"),
                    "stock update preserves retired storage columns");
            }return null;});
        }
    }
    private static JTextField findAtc(java.awt.Container parent) {
        for(var component:parent.getComponents()) {
            if(component instanceof JTextField field&&"ATC code".equals(field.getAccessibleContext().getAccessibleName()))return field;
            if(component instanceof java.awt.Container child){JTextField field=findAtc(child);if(field!=null)return field;}
        }return null;
    }
    private static <T>T find(java.awt.Container parent,Class<T> type) {
        for(var component:parent.getComponents()) {
            if(type.isInstance(component))return type.cast(component);
            if(component instanceof java.awt.Container child){T found=find(child,type);if(found!=null)return found;}
        }return null;
    }
    private static void fixture(Path path)throws Exception {
        String ns="http://schemas.openxmlformats.org/spreadsheetml/2006/main";
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(path))) {
            part(zip,"xl/workbook.xml","<workbook xmlns='"+ns+"' xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships'><sheets><sheet name='Catalog' sheetId='1' r:id='r1'/></sheets></workbook>");
            part(zip,"xl/_rels/workbook.xml.rels","<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'><Relationship Id='r1' Target='worksheets/catalog.xml'/></Relationships>");
            part(zip,"xl/sharedStrings.xml","<sst xmlns='"+ns+"'><si><t>Alpha available</t></si></sst>");
            String[][] rows={{"Philippines","Medicine","WHO Model List of Essential Medications","ATC code secondary","ATC code primary"},
                {"0","Alpha absent","0","","A01"},{"1","Alpha available","1","A02","A01"},
                {"1","Contains alpha","0","","C01"},{"0","Beta","1","","B02"},
                {"1"," alpha AVAILABLE ","1","","A01"},{"0","","0","",""}};
            StringBuilder xml=new StringBuilder("<worksheet xmlns='"+ns+"'><sheetData>");
            for(int r=0;r<rows.length;r++) {
                xml.append("<row r='").append(r+1).append("'>");
                for(int c=0;c<rows[r].length;c++) {
                    xml.append("<c r='").append((char)('A'+c)).append(r+1).append("' ");
                    if(r==2&&c==1)xml.append("t='s'><v>0</v></c>");
                    else xml.append("t='inlineStr'><is><t>").append(rows[r][c]).append("</t></is></c>");
                }xml.append("</row>");
            }part(zip,"xl/worksheets/catalog.xml",xml.append("</sheetData></worksheet>").toString());
        }
    }
    private static void part(ZipOutputStream zip,String name,String text)throws Exception {
        zip.putNextEntry(new ZipEntry(name));zip.write(text.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
}
