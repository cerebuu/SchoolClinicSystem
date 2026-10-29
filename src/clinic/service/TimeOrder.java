package clinic.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** Orders rows by time descending, with insertion order descending for timestamp ties. */
public final class TimeOrder {
    private TimeOrder() {}

    public static <T> List<T> newestFirst(Collection<T> rows, Function<T, LocalDateTime> timestamp) {
        List<T> ordered = new ArrayList<>(rows);
        Collections.reverse(ordered);
        ordered.sort(Comparator.comparing(timestamp, Comparator.nullsLast(Comparator.reverseOrder())));
        return List.copyOf(ordered);
    }
}
