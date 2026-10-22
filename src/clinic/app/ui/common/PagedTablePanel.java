package clinic.app.ui.common;
import clinic.app.Theme;
import java.awt.*;
import javax.swing.*;
import javax.swing.event.*;
public final class PagedTablePanel<T> extends JPanel {
    public static final int PAGE_SIZE=25;
    private final PagedTableModel<T> model;
    private final PageSource<T> source;
    private final JTable table;
    private final JTextField search=new JTextField(24);
    private final JLabel state=new JLabel("Loading...");
    private final JButton prev=Theme.button("Prev", false), next=Theme.button("Next", false);
    private final Timer debounce;
    private SwingWorker<?, ?> worker;
    private int page, total, generation;
    private String emptyText = "No matching records";
    private java.util.function.Function<String, String> emptyMessage;
    public PagedTablePanel(PagedTableModel<T> model, PageSource<T> source) {
        this(model, source, "Search / filter");
    }
    public PagedTablePanel(PagedTableModel<T> model, PageSource<T> source, String filterLabel) {
        this(model, source, filterLabel, true);
    }
    public PagedTablePanel(PagedTableModel<T> model, PageSource<T> source, String filterLabel, boolean showSearchControls) {
        super(new BorderLayout(Theme.GAP, Theme.GAP));
        this.model=model;
        this.source=source;
        table=new JTable(model);
        Theme.table(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel label = new JLabel(filterLabel);
        label.setLabelFor(search);
        controls.add(label);
        controls.add(search);
        JButton refresh=Theme.button("Refresh", false);
        controls.add(refresh);
        if (showSearchControls) add(controls, BorderLayout.NORTH);
        JScrollPane scroll=new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        add(scroll, BorderLayout.CENTER);
        JPanel footer=new JPanel(new FlowLayout(FlowLayout.LEFT));
        footer.add(prev);
        footer.add(next);
        footer.add(state);
        add(footer, BorderLayout.SOUTH);
        prev.addActionListener(e->setPage(page-1));
        next.addActionListener(e->setPage(page+1));
        refresh.addActionListener(e->reload());
        debounce=new Timer(250, e-> {
            page=0;
            reload();
        });
        debounce.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                debounce.restart();
            }
            public void removeUpdate(DocumentEvent e) {
                debounce.restart();
            }
            public void changedUpdate(DocumentEvent e) {
                debounce.restart();
            }
        });
        search.getAccessibleContext().setAccessibleName(filterLabel);
        reload();
    }
    public void setEmptyMessage(java.util.function.Function<String, String> message) { emptyMessage = message; }
    /** For external debounced controls: one immediate load, without the internal debounce. */
    public void applyFilter(String query) {
        search.setText(query);
        debounce.stop();
        page = 0;
        reload();
    }
    public void setEmptyText(String text) {
        emptyText = java.util.Objects.requireNonNull(text);
    }
    public void setFilter(String q) {
        search.setText(q);
    }
    public void setPage(int p) {
        page=Math.max(0, p);
        reload();
    }
    public int totalCount() {
        return total;
    }
    public T selected() {
        int r=table.getSelectedRow();
        return r<0?null:model.row(table.convertRowIndexToModel(r));
    }
    public void reload() {
        if(worker!=null)worker.cancel(true);
        int version=++generation, p=page;
        String q=search.getText().trim();
        state.setText("Loading...");
        prev.setEnabled(false);
        next.setEnabled(false);
        table.setEnabled(false);
        worker=AsyncLoader.load(()->source.fetch(p, PAGE_SIZE, q), result-> {
            if(version!=generation)return;
            total=result.total();
            int last=Math.max(0, (total-1)/PAGE_SIZE);
            if(page>last) {
                page=last;
                reload();
                return;
            }
            model.replace(result);
            table.setEnabled(true);
            state.setText(total==0?(emptyMessage == null ? (q.isEmpty()?emptyText:"No matching records") : emptyMessage.apply(q)):"Page "+(page+1)+" of "+(last+1)+" | "+total+" records");
            prev.setEnabled(page>0);
            next.setEnabled(page<last);
        }, err-> {
            if(version==generation) {
                model.replace(new clinic.service.PageResult<>(java.util.List.of(), 0));
                state.setText("Unable to load records. Use Refresh to retry.");
                table.setEnabled(false);
            }
        });
    }
    public void removeNotify() {
        generation++;
        debounce.stop();
        if(worker!=null)worker.cancel(true);
        super.removeNotify();
    }
    public void addNotify() {
        super.addNotify();
        reload();
    }
}
