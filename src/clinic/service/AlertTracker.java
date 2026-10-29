package clinic.service;

import clinic.model.Alert;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tracks detector state changes and per-user alert views without retaining alert text. */
public final class AlertTracker {
    public record Entry(String alertKey,String scopeKey,String fingerprint,boolean active,long generation) {}
    public record Seen(String userId,String alertKey,long generation) {}

    private final Map<String,Entry> entries=new LinkedHashMap<>();
    private final Map<String,Seen> seen=new LinkedHashMap<>();

    public AlertTracker() {}

    public AlertTracker(Collection<Entry> savedEntries,Collection<Seen> savedSeen) {
        if(savedEntries!=null)savedEntries.forEach(entry->entries.put(entry.alertKey(),entry));
        if(savedSeen!=null)savedSeen.forEach(item->seen.put(seenKey(item.userId(),item.alertKey()),item));
    }

    public synchronized List<Alert> reconcile(String scope,List<Alert> detected) {
        String scopeKey=scope==null?"*":scope;
        Set<String> current=new LinkedHashSet<>();
        List<Alert> tracked=new ArrayList<>(detected.size());
        for(Alert alert:detected) {
            String key=hash(scopeKey+"\u0000"+alert.getTrackingKey());
            current.add(key);
            Entry previous=entries.get(key);
            long generation=previous==null?1:previous.generation();
            if(previous!=null&&(!previous.active()||!previous.fingerprint().equals(alert.getFingerprint())))
                generation=Math.addExact(generation,1);
            entries.put(key,new Entry(key,scopeKey,alert.getFingerprint(),true,generation));
            tracked.add(alert.withTrackingState(key,generation));
        }
        entries.replaceAll((key,entry)->entry.scopeKey().equals(scopeKey)&&entry.active()&&!current.contains(key)
            ?new Entry(entry.alertKey(),entry.scopeKey(),entry.fingerprint(),false,entry.generation()):entry);
        return List.copyOf(tracked);
    }

    public synchronized int unseenCount(String userId,List<Alert> current) {
        String owner=clinic.util.Validation.required(userId,"User ID");
        int count=0;
        for(Alert alert:current) {
            Seen viewed=seen.get(seenKey(owner,alert.getTrackingKey()));
            if(viewed==null||alert.getGeneration()>viewed.generation())count++;
        }
        return count;
    }

    public synchronized void markSeen(String userId,Collection<Alert> alerts) {
        String owner=clinic.util.Validation.required(userId,"User ID");
        for(Alert alert:alerts) {
            if(alert==null||alert.getGeneration()<1||alert.getTrackingKey()==null)continue;
            String key=seenKey(owner,alert.getTrackingKey());
            Seen previous=seen.get(key);
            if(previous==null||alert.getGeneration()>previous.generation())
                seen.put(key,new Seen(owner,alert.getTrackingKey(),alert.getGeneration()));
        }
    }

    public synchronized List<Entry> entries() { return List.copyOf(entries.values()); }
    public synchronized List<Seen> seen() { return List.copyOf(seen.values()); }
    public synchronized Set<String> scopes() {
        Set<String> scopes=new LinkedHashSet<>();
        entries.values().forEach(entry->scopes.add(entry.scopeKey()));
        return scopes;
    }

    private static String seenKey(String userId,String alertKey) { return userId+"\u0000"+alertKey; }
    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.",impossible);
        }
    }
}
