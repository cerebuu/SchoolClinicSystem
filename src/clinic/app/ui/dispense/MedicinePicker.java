package clinic.app.ui.dispense;

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
public final class MedicinePicker extends JPanel {
    private static final int PAGE_SIZE = 25;
    private static final int MAX_CACHED_PAGES = 4;
    private final JComboBox<Object> choices = new JComboBox<>();
    private final Map<String, PageResult<Medicine>> cache = new LinkedHashMap<>();
    private final ApplicationContext app;
    private final Runnable changed;
    private final JTextField editor;
    private final javax.swing.Timer debounce;
    private final LiveRefresh live;
    private Medicine selected;
    private int page;
    private int generation;
    private boolean loading;
    private boolean reopenPopup;
    private String query = "";

    public MedicinePicker(ApplicationContext app, Runnable changed) {
        super(new java.awt.BorderLayout());
        this.app = app;
        this.changed = changed;
        setOpaque(false);
        choices.setEditable(true);
        choices.getAccessibleContext().setAccessibleName("Medicine");
        choices.setToolTipText("Loading medicines...");
        editor = (JTextField) choices.getEditor().getEditorComponent();
        editor.getAccessibleContext().setAccessibleName("Search medicines");
        editor.setToolTipText("Type to filter medicines");
        Theme.field(choices);
        Theme.field(editor);
        choices.setPreferredSize(new Dimension(0, DispenseLayout.COMBO_HEIGHT));
        choices.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean active, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, active, focus);
                if (value instanceof Medicine medicine) {
                    label.setText(medicine.getDisplayName() + " — stock " + medicine.getQuantity());
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
        add(choices, java.awt.BorderLayout.CENTER);
        debounce = new javax.swing.Timer(250, event -> {
            page = 0;
            load();
        });
        debounce.setRepeats(false);
        editor.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                if (!loading && !(choices.getSelectedItem() instanceof PageOption)) {
                    query = editor.getText().trim();
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
    public void refresh() {
        cache.clear();
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
            selected = medicine;
            restoreEditor();
            changed.run();
        }
    }
    private void restoreEditor() {
        loading = true;
        query = selected == null ? "" : selected.getDisplayName();
        editor.setText(query);
        loading = false;
    }
    private void load() {
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
                choices.setEnabled(true);
                setState("Unable to load medicines. Retry the search.");
            }
        });
    }
    private void showPage(PageResult<Medicine> result, String query, int requestedPage) {
        loading = true;
        if (selected != null) {
            selected = result.rows().stream()
                .filter(medicine -> medicine.getProduct().key().equals(selected.getProduct().key()))
                .findFirst().orElse(selected);
        }
        DefaultComboBoxModel<Object> model = new DefaultComboBoxModel<>();
        if (requestedPage > 0) model.addElement(PageOption.PREVIOUS);
        result.rows().forEach(model::addElement);
        if ((long) (requestedPage + 1) * PAGE_SIZE < result.total()) model.addElement(PageOption.NEXT);
        choices.setModel(model);
        choices.setSelectedItem(null);
        editor.setText(query);
        loading = false;
        choices.setEnabled(true);
        setState(result.total() + " medicines | page " + (requestedPage + 1));
        if (reopenPopup) {
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
    private enum PageOption {
        PREVIOUS("← Previous medicines"),
        NEXT("More medicines →");

        private final String label;
        PageOption(String label) { this.label = label; }
    }
}
