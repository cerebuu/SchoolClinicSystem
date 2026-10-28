package clinic.app.ui.common;

import clinic.app.ApplicationContext;
import clinic.app.DataTopic;
import clinic.app.Theme;
import clinic.app.ui.common.AsyncLoader;
import clinic.app.ui.common.LiveRefresh;
import clinic.model.Medicine;
import clinic.service.PageResult;
import java.awt.Component;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Searchable medicine selector; popup navigation loads bounded pages on demand. */
public final class MedicinePicker extends JPanel implements clinic.app.ui.common.popup.FormValueComponent {
    private static final int PAGE_SIZE = 50;
    private static final int MAX_CACHED_PAGES = 4;
    private final JComboBox<Object> choices = new JComboBox<>();
    private final Map<String, PageResult<Medicine>> cache = new LinkedHashMap<>();
    private final ApplicationContext app;
    private final Runnable changed;
    private final JTextField editor;
    private final javax.swing.Timer debounce;
    private final LiveRefresh live;
    private final boolean catalogMode;
    private clinic.service.MedicineCatalog catalog;
    private final JTextField atc=new JTextField();
    private final JLabel error=new JLabel(" ");
    private Medicine selected;
    private int page;
    private int generation;
    private boolean loading;
    private boolean reopenPopup;
    private boolean reconcileSelectionAfterRefresh;
    private String query = "";

    public MedicinePicker(ApplicationContext app, Runnable changed) {
        this(app,changed,false);
    }
    public MedicinePicker(ApplicationContext app,Runnable changed,boolean catalogMode) {
        super(new java.awt.BorderLayout(0,Theme.SPACE_1));
        this.catalogMode=catalogMode;
        this.app = app;
        this.changed = changed;
        setOpaque(false);
        choices.setEditable(true);
        choices.setMaximumRowCount(8);
        choices.getAccessibleContext().setAccessibleName("Medicine");
        choices.setToolTipText("Loading medicines...");
        editor = (JTextField) choices.getEditor().getEditorComponent();
        editor.getAccessibleContext().setAccessibleName("Search medicines");
        editor.setToolTipText("Type to filter medicines");
        Theme.field(choices);
        Theme.field(editor);
        choices.setPreferredSize(new Dimension(360, Theme.CONTROL_HEIGHT));
        choices.setMinimumSize(new Dimension(240, Theme.CONTROL_HEIGHT));
        choices.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean active, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, active, focus);
                if (value instanceof Medicine medicine) {
                    label.setText("<html>"+escape(medicine.getName())+"<br><font color='#"
                        +String.format("%06x",Theme.MUTED.getRGB()&0xffffff)+"'>"
                        +"<small>"+escape(medicine.getProduct().atcLabel())+(catalogMode?"":" · "+medicine.getQuantity()+" in stock")+"</small></font></html>");
                    label.setEnabled(medicine.getQuantity() > 0);
                } else if (value instanceof PageOption option) {
                    label.setText(option.label);
                } else {
                    label.setText("Select medicine");
                }
                label.setBackground(active ? Theme.BRAND_TINT : Theme.WHITE);
                label.setForeground(Theme.INK);
                return label;
            }
        });
        add(choices, java.awt.BorderLayout.NORTH);
        atc.setEditable(false);atc.getAccessibleContext().setAccessibleName("ATC code");Theme.field(atc);
        JPanel details=new JPanel(new java.awt.BorderLayout(Theme.SPACE_2,0));details.setOpaque(false);
        details.add(new JLabel("ATC code"),java.awt.BorderLayout.WEST);details.add(atc,java.awt.BorderLayout.CENTER);
        add(details,java.awt.BorderLayout.CENTER);error.setForeground(Theme.DANGER);
        error.setFont(Theme.font(Theme.TYPE_CAPTION,java.awt.Font.PLAIN));add(error,java.awt.BorderLayout.SOUTH);
        debounce = new javax.swing.Timer(200, event -> {
            page = 0;
            load();
        });
        debounce.setRepeats(false);
        editor.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                if (!loading && !(choices.getSelectedItem() instanceof PageOption)) {
                    query = editor.getText().trim();
                    if (selected != null && !query.equals(selected.getDisplayName())) {
                        selected = null;
                        selectionChanged();
                    }
                    if(selected==null) {
                        atc.setText("");
                        error.setText(query.isBlank()?" ":"Choose a medicine from the list.");
                        firePropertyChange(READY_PROPERTY,null,false);
                    }
                    generation++;
                    debounce.restart();
                }
            }
            @Override public void insertUpdate(DocumentEvent event) { changed(); }
            @Override public void removeUpdate(DocumentEvent event) { changed(); }
            @Override public void changedUpdate(DocumentEvent event) { changed(); }
        });
        choices.addActionListener(event -> choose(choices.getSelectedItem()));
        live = new LiveRefresh(this, app.changes, this::refresh, DataTopic.INVENTORY);
    }

    public Medicine selected() {
        return selected;
    }
    public boolean hasSearchInput() {
        // JComboBox can briefly put the selected Medicine object's toString() in
        // its editor while a page model is replaced. Only the user's query is
        // relevant when deciding whether an optional consultation row is blank.
        return !query.isBlank();
    }
    public void refresh() {
        cache.clear();
        if (selected != null) {
            // Selection puts its display label in the search field; load from the first
            // page to reconcile it against the current inventory.
            page = 0;
            reconcileSelectionAfterRefresh = true;
        }
        load();
    }

    private void choose(Object value) {
        if (loading) return;
        if (value instanceof PageOption option) {
            loading = true;
            editor.setText(query);
            loading = false;
            page += option == PageOption.NEXT ? 1 : -1;
            reopenPopup = true;
            load();
        } else if (value instanceof Medicine medicine) {
            if (medicine.getQuantity() <= 0) {
                restoreEditor();
                setState("Out of stock — unavailable");
                return;
            }
            debounce.stop();
            page = 0;
            selected = medicine;
            restoreEditor();
            selectionChanged();
        }
    }
    private void restoreEditor() {
        loading = true;
        query = selected == null ? "" : selected.getDisplayName();
        editor.setText(query);
        loading = false;
    }
    private void load() {
        if(catalogMode) {
            if(catalog==null) {
                MedicineCatalogCache.load(value->{catalog=value;load();},problem->{
                    error.setText(problem.getMessage());choices.setEnabled(false);
                });
                return;
            }
            var rows=catalog.search(query).stream().map(e->new Medicine(e.product(),1,
                java.time.LocalDate.MAX,java.time.LocalDate.now(),"")).toList();
            showPage(new PageResult<>(rows,rows.size()),query,0);return;
        }
        int version = ++generation;
        int requestedPage = page;
        String currentQuery = query;
        String key = requestedPage + "\n" + currentQuery;
        choices.setEnabled(false);
        setState("Loading medicines...");
        PageResult<Medicine> cached = cache.get(key);
        if (cached != null) {
            showPage(cached, currentQuery, requestedPage);
            return;
        }
        AsyncLoader.load(() -> app.dispensableMedicines(requestedPage, PAGE_SIZE, currentQuery), result -> {
            if (version != generation || !live.active()) return;
            if (cache.size() >= MAX_CACHED_PAGES) cache.remove(cache.keySet().iterator().next());
            cache.put(key, result);
            showPage(result, currentQuery, requestedPage);
        }, error -> {
            if (version == generation && live.active()) {
                reconcileSelectionAfterRefresh = false;
                choices.setEnabled(true);
                setState("Unable to load medicines. Retry the search.");
            }
        });
    }
    private void showPage(PageResult<Medicine> result, String query, int requestedPage) {
        loading = true;
        boolean selectionInvalidated = false;
        if (selected != null) {
            Medicine refreshed = result.rows().stream()
                .filter(medicine -> medicine.getProduct().key().equals(selected.getProduct().key()))
                .findFirst().orElse(null);
            if (refreshed != null && refreshed.getQuantity() > 0) selected = refreshed;
            else if (reconcileSelectionAfterRefresh) {
                selected = null;
                selectionInvalidated = true;
            }
        }
        reconcileSelectionAfterRefresh = false;
        DefaultComboBoxModel<Object> model = new DefaultComboBoxModel<>();
        if (requestedPage > 0) model.addElement(PageOption.PREVIOUS);
        result.rows().stream().filter(m->m.getQuantity()>0).forEach(model::addElement);
        if (!catalogMode && (long) (requestedPage + 1) * PAGE_SIZE < result.total()) model.addElement(PageOption.NEXT);
        choices.setModel(model);
        choices.setSelectedItem(null);
        editor.setText(query);
        loading = false;
        choices.setEnabled(true);
        atc.setText(selected==null?"":selected.getProduct().atcLabel());
        setState(result.total() + " medicines | page " + (requestedPage + 1));
        if (selectionInvalidated) {
            // A refreshed search may no longer contain the previously selected product.
            // Keep the typed query visible so the user can choose another available item.
            selectionChanged();
        }
        if (reopenPopup || editor.isFocusOwner()&&!query.isBlank()) {
            reopenPopup = false;
            SwingUtilities.invokeLater(() -> {
                if (isShowing()) choices.setPopupVisible(true);
            });
        }
    }
    private void setState(String message) {
        choices.setToolTipText(message);
        choices.getAccessibleContext().setAccessibleDescription(message);
    }
    @Override
    public void removeNotify() {
        generation++;
        debounce.stop();
        super.removeNotify();
    }
    private static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private void selectionChanged() {
        atc.setText(selected==null?"":selected.getProduct().atcLabel());
        error.setText(selected==null&&hasSearchInput()?"Choose a medicine from the list.":" ");
        firePropertyChange(READY_PROPERTY,null,isFormReady());changed.run();
    }
    @Override public boolean isFormReady(){return selected!=null;}
    @Override public boolean validateForm(){selectionChanged();return isFormReady();}
    @Override public String formValue(){return selected==null?"":selected.getName();}
    private enum PageOption {
        PREVIOUS("← Previous medicines"),
        NEXT("More medicines →");

        private final String label;
        PageOption(String label) { this.label = label; }
    }
}
