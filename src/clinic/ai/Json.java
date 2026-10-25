package clinic.ai;

import java.util.*;

/** Bounded JSON reader for provider responses. No evaluation, reflection, or lenient recovery. */
final class Json {
    private final String source;
    private int pos;
    private Json(String source) { this.source = source; }
    static Object parse(String source) {
        if (source == null || source.length() > 262144) throw new IllegalArgumentException("Invalid JSON size");
        Json parser = new Json(source);
        Object value = parser.value(0);
        parser.space();
        if (parser.pos != source.length()) throw new IllegalArgumentException("Trailing JSON");
        return value;
    }
    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> { if (c < 32) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }
    private void space() { while (pos < source.length() && " \r\n\t".indexOf(source.charAt(pos)) >= 0) pos++; }
    private boolean take(char c) { space(); if (pos < source.length() && source.charAt(pos) == c) { pos++; return true; } return false; }
    private void need(char c) { if (!take(c)) throw new IllegalArgumentException("Invalid JSON delimiter"); }
    private Object value(int depth) {
        if (depth > 32) throw new IllegalArgumentException("JSON nesting limit");
        space();
        if (pos >= source.length()) throw new IllegalArgumentException("Incomplete JSON");
        if (source.charAt(pos) == '"') return string();
        if (take('{')) {
            Map<String,Object> map = new LinkedHashMap<>();
            if (take('}')) return map;
            do { space(); String key=string(); need(':'); if (map.containsKey(key)) throw new IllegalArgumentException("Duplicate JSON key"); map.put(key,value(depth+1)); } while(take(','));
            need('}'); return map;
        }
        if (take('[')) {
            List<Object> list=new ArrayList<>();
            if(take(']')) return list;
            do { list.add(value(depth+1)); } while(take(','));
            need(']'); return list;
        }
        for(String literal : List.of("true","false","null")) if(source.startsWith(literal,pos)) {
            pos+=literal.length(); return literal.equals("null")?null:Boolean.valueOf(literal);
        }
        var matcher=java.util.regex.Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?").matcher(source.substring(pos));
        if(!matcher.lookingAt()) throw new IllegalArgumentException("Invalid JSON value");
        pos+=matcher.end(); return new java.math.BigDecimal(matcher.group());
    }
    private String string() {
        need('"'); StringBuilder out=new StringBuilder();
        while(pos<source.length()) {
            char c=source.charAt(pos++);
            if(c=='"') return out.toString();
            if(c<32) throw new IllegalArgumentException("Unescaped control character");
            if(c!='\\') { out.append(c); continue; }
            if(pos==source.length()) break;
            char escaped=source.charAt(pos++);
            switch(escaped) {
                case '"','\\','/' -> out.append(escaped);
                case 'b' -> out.append('\b'); case 'f' -> out.append('\f');
                case 'n' -> out.append('\n'); case 'r' -> out.append('\r'); case 't' -> out.append('\t');
                case 'u' -> { if(pos+4>source.length()) throw new IllegalArgumentException("Incomplete escape"); out.append((char)Integer.parseInt(source.substring(pos,pos+4),16)); pos+=4; }
                default -> throw new IllegalArgumentException("Invalid escape");
            }
        }
        throw new IllegalArgumentException("Incomplete string");
    }
}
