package clinic.service;
import java.util.*;
import java.util.function.Predicate;
public record PageResult<T>(List<T> rows, int total) {
    public PageResult {
        rows=List.copyOf(rows);
    }
    public static <T> PageResult<T> select(Iterable<T> source, int page, int size, Predicate<T> filter) {
        if(page<0||size<1)throw new IllegalArgumentException("Invalid page.");
        List<T> rows=new ArrayList<>(size);
        int count=0;
        long offset=(long)page*size;
        for(T item:source) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            if(filter.test(item)) {
                if(count>=offset&&rows.size()<size)rows.add(item);
                count++;
            }
        }
        return new PageResult<>(rows, count);
    }
}
