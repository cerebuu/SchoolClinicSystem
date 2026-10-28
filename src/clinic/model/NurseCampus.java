package clinic.model;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The three campus assignments permitted for school nurses. */
public enum NurseCampus {
    BAJADA("uic-bajada", "UIC - Bajada"),
    ANNEX("uic-annex", "UIC - Annex"),
    MAIN("uic-main", "UIC - Main");

    private final String id;
    private final String name;

    NurseCampus(String id, String name) {
        this.id=id;
        this.name=name;
    }

    public String id() { return id; }
    public String displayName() { return name; }
    public Campus campus() { return new Campus(id,name); }

    public static List<NurseCampus> choices() { return List.of(values()); }

    public static Optional<NurseCampus> forId(String id) {
        return Arrays.stream(values()).filter(campus -> campus.id.equals(id)).findFirst();
    }

    public static Optional<NurseCampus> forName(String name) {
        return Arrays.stream(values()).filter(campus -> campus.name.equals(name)).findFirst();
    }
}
