package clinic.app.ui.common;
import clinic.app.Theme;
import java.awt.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.DefaultTableCellRenderer;
public final class PagedTablePanel<T> extends Card {
    public static final int PAGE_SIZE=25;
    private final PagedTableModel<T> model;
    private final PageSource<T> source;
    private final JTable table;
    private final JScrollPane scroll;
    private final JTextField search=new JTextField(24);
    private final JLabel state=new JLabel("Loading...");
    private final JButton prev=Theme.button("Prev", false), next=Theme.button("Next", false);
    private final Timer debounce;
    private SwingWorker<?, ?> worker;
    private int page, total, generation;
    private int refreshCount;
    private LiveRefresh live;
    private final JLabel updated=new JLabel(" ");
    private JButton refreshButton;
    private String emptyText = "No matching records";
    private java.util.function.Function<String, String> emptyMessage;
    public PagedTablePanel(PagedTableModel<T> model, PageSource<T> source) {
        this(model, source, "Search / filter");
    }
    public PagedTablePanel(PagedTableModel<T> model, PageSource<T> source, String filterLabel) {
        this(model, source, filterLabel, true);
    }
    public PagedTablePanel(PagedTableModel<T> model, PageSource<T> source, String filterLabel, boolean showSearchControls) {
        super();
        setLayout(new BorderLayout(Theme.GAP, Theme.GAP));
        this.model=model;
        this.source=source;
        table=new JTable(model);
        Theme.table(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class, new TableRowRenderer());
        table.setDefaultRenderer(Number.class, new TableRowRenderer());
        JPanel controls=new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        controls.setOpaque(false);
        JLabel label = new JLabel(filterLabel);
        label.setLabelFor(search);
        Theme.field(search);
        if(showSearchControls){controls.add(label, BorderLayout.WEST);controls.add(search, BorderLayout.CENTER);}
        JButton refresh=Theme.button("Refresh", false);
        refreshButton=refresh;
        refresh.getAccessibleContext().setAccessibleName("Refresh table");
        JPanel refreshArea=new JPanel(new FlowLayout(FlowLayout.RIGHT,Theme.SPACE_2,0));refreshArea.setOpaque(false);
        updated.setForeground(Theme.MUTED);refreshArea.add(updated);refreshArea.add(refresh);
        controls.add(refreshArea, BorderLayout.EAST);add(controls, BorderLayout.NORTH);
        scroll=new JScrollPane(table, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBackground(Theme.WHITE);
        scroll.getViewport().setBackground(Theme.WHITE);
        scroll.setWheelScrollingEnabled(true);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        add(scroll, BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(Theme.GAP, 0));
        footer.setOpaque(false);
        JPanel paging=new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_2, 0));
        paging.setOpaque(false);
        paging.add(prev);
        paging.add(next);
        footer.add(paging, BorderLayout.WEST);
        state.setForeground(Theme.MUTED);
        footer.add(state, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        prev.addActionListener(e->setPage(page-1));
        next.addActionListener(e->setPage(page+1));
        refresh.addActionListener(e->reload());
        debounce=new Timer(300, e-> {
            page=0;
            reload();
        });
        debounce.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                generation++;
                debounce.restart();
            }
            public void removeUpdate(DocumentEvent e) {
                generation++;
                debounce.restart();
            }
            public void changedUpdate(DocumentEvent e) {
                debounce.restart();
            }
        });
        search.getAccessibleContext().setAccessibleName(filterLabel);
    }
    public PagedTablePanel<T> refreshOn(clinic.app.DataChangeBus bus,clinic.app.DataTopic... topics) {
        live=new LiveRefresh(this,bus,this::reload,topics);return this;
    }
    public int refreshCount(){return refreshCount;}
    public String modelType(){return model.getClass().getSimpleName();}
    public void setDashboardViewport(int visibleRows,int rowHeight) {
        table.setRowHeight(rowHeight);
        int height=table.getTableHeader().getPreferredSize().height+visibleRows*rowHeight;
        Dimension preferred=scroll.getPreferredSize();
        scroll.setPreferredSize(new Dimension(preferred.width,height)); scroll.setMinimumSize(new Dimension(0,height));
    }
    public int currentPage(){return page;}
    public String filterText(){return search.getText();}
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
        if (!table.isEnabled()) return null;
        int r=table.getSelectedRow();
        return r<0?null:model.row(table.convertRowIndexToModel(r));
    }
    public void addSelectionListener(Runnable listener) {
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) listener.run();
        });
    }
    public void reload() {
        if(live!=null&&!live.active())return;
        if(debounce.isRunning())return;
        if(worker!=null)worker.cancel(false);
        int version=++generation, p=page;
        refreshCount++;
        Object selectedKey=table.getSelectedRow()<0?null:model.key(table.convertRowIndexToModel(table.getSelectedRow()));
        Point position=scroll.getViewport().getViewPosition();
        String q=search.getText().trim();
        state.setText("Loading...");
        prev.setEnabled(false);
        next.setEnabled(false);
        scroll.setViewportView(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        worker=AsyncLoader.load(()->source.fetch(p, PAGE_SIZE, q), result-> {
            if(version!=generation||(live!=null&&!live.active()))return;
            total=result.total();
            int last=Math.max(0, (total-1)/PAGE_SIZE);
            if(page>last) {
                page=last;
                reload();
                return;
            }
            model.replace(result);
            for(int i=0;i<model.getRowCount();i++)if(selectedKey!=null&&selectedKey.equals(model.key(i))){table.setRowSelectionInterval(i,i);break;}
            table.setEnabled(true);
            if (total == 0) {
                String message = emptyMessage == null
                    ? (q.isEmpty() ? emptyText : "No matching records")
                    : emptyMessage.apply(q);
                state.setText("0 records");
                scroll.setColumnHeaderView(null);
                scroll.setViewportView(new EmptyState(message, q.isEmpty()
                    ? "Records will appear here when they are available."
                    : "Try a different search or clear the filter."));
            } else {
                state.setText("Page "+(page+1)+" of "+(last+1)+" | "+total+" records");
                scroll.setColumnHeaderView(table.getTableHeader());
                scroll.setViewportView(table);
            }
            prev.setEnabled(page>0);
            next.setEnabled(page<last);
            updated.setText("Updated just now");updated.setToolTipText(java.time.LocalTime.now().withNano(0).toString());
                refreshButton.setText("Refresh");
            SwingUtilities.invokeLater(()->{if(version==generation&&total>0)scroll.getViewport().setViewPosition(position);});
        }, err-> {
            if(version==generation&&(live==null||live.active())) {
                total=0;
                model.replace(new clinic.service.PageResult<>(java.util.List.of(), 0));
                state.setText("Unable to load records. Use Refresh to retry.");
                updated.setText("Refresh failed");
                refreshButton.setText("Retry");
                scroll.setColumnHeaderView(null);
                scroll.setViewportView(new EmptyState("Unable to load records",
                    "Use Refresh to retry. Your current filter is preserved."));
                table.setEnabled(false);
            }
        });
    }
    public void removeNotify() {
        generation++;
        debounce.stop();
        if(worker!=null)worker.cancel(false);
        super.removeNotify();
    }
    public void addNotify() {
        super.addNotify();
        if(live==null)reload();
    }

}
