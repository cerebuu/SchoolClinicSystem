import clinic.app.Theme;
import clinic.app.ui.common.*;
import clinic.service.PageResult;
import java.util.*;
import javax.swing.*;
public final class PagedTableDemo {
    public static PageResult<Integer> fetch(int page, int size, String query) {
        Iterable<Integer> source=()->new Iterator<>() {
            int value;
            public boolean hasNext() {
                return value<10000;
            }
            public Integer next() {
                return ++value;
            }
        };
        return PageResult.select(source, page, size, n->("Dummy record "+n).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)));
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(()-> {
            Theme.install();
            JFrame f=new JFrame("10,000 rows - 25 loaded per page");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.add(new PagedTablePanel<>(new DummyTableModel(), PagedTableDemo::fetch));
            f.setSize(900, 650);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}
