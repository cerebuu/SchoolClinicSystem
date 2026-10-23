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
        table.setDefaultRenderer(Object.class, new ZebraRenderer());
        table.setDefaultRenderer(Number.class, new ZebraRenderer());
        JPanel controls=new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        controls.setOpaque(false);
        JLabel label = new JLabel(filterLabel);
        label.setLabelFor(search);
        Theme.field(search);
        controls.add(label, BorderLayout.WEST);
        controls.add(search, BorderLayout.CENTER);
        JButton refresh=Theme.button("Refresh", false);
        refresh.getAccessibleContext().setAccessibleName("Refresh table");
        controls.add(refresh, BorderLayout.EAST);
        if (showSearchControls) add(controls, BorderLayout.NORTH);
        scroll=new JScrollPane(table, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBackground(Theme.WHITE);
        scroll.getViewport().setBackground(Theme.WHITE);
        scroll.setWheelScrollingEnabled(true);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);
        scroll.setColumnHeaderView(table.getTableHeader());
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
        if (!table.isEnabled()) return null;
        int r=table.getSelectedRow();
        return r<0?null:model.row(table.convertRowIndexToModel(r));
    }
    public void reload() {
        if(worker!=null)worker.cancel(false);
        int version=++generation, p=page;
        String q=search.getText().trim();
        state.setText("Loading...");
        prev.setEnabled(false);
        next.setEnabled(false);
        table.setEnabled(false);
        table.clearSelection();
        scroll.setViewportView(table);
        scroll.setColumnHeaderView(table.getTableHeader());
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
        }, err-> {
            if(version==generation) {
                total=0;
                model.replace(new clinic.service.PageResult<>(java.util.List.of(), 0));
                state.setText("Unable to load records. Use Refresh to retry.");
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
        reload();
    }

    private final class ZebraRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable owner, Object value,
                boolean selected, boolean focused, int row, int column) {
            String heading = owner.getColumnName(column);
            if (value instanceof Boolean allowed) {
                StatusChip chip = new StatusChip(allowed ? "Allowed" : "Denied",
                    allowed ? StatusChip.Kind.SUCCESS : StatusChip.Kind.DANGER);
                chip.setSelected(selected);
                return chip;
            }
            if ("Status".equalsIgnoreCase(heading) || value instanceof Enum<?>) {
                String text = String.valueOf(value);
                String lower = text.toLowerCase(java.util.Locale.ROOT);
                StatusChip.Kind kind = lower.contains("denied") || lower.contains("failed")
                    || lower.contains("expired") || lower.contains("error")
                    ? StatusChip.Kind.DANGER
                    : lower.contains("pending") || lower.contains("waiting")
                        ? StatusChip.Kind.WARNING
                        : lower.contains("sent") || lower.contains("allowed")
                            || lower.equals("active") || lower.contains("success")
                                ? StatusChip.Kind.SUCCESS : StatusChip.Kind.NEUTRAL;
                StatusChip chip = new StatusChip(text, kind);
                chip.setSelected(selected);
                return chip;
            }
            super.getTableCellRendererComponent(owner, value, selected, focused, row, column);
            setOpaque(true);
            setHorizontalAlignment(value instanceof Number ? RIGHT : LEFT);
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            setBackground(selected ? Theme.BRAND_TINT
                : row % 2 == 0 ? Theme.WHITE : Theme.ZEBRA);
            setForeground(selected ? Theme.BRAND_DARK : Theme.INK);
            setToolTipText(value == null ? null : String.valueOf(value));
            if (focused) {
                setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.FOCUS),
                    BorderFactory.createEmptyBorder(0, 11, 0, 11)));
            }
            return this;
        }
    }
}
