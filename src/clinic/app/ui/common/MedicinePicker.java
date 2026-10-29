package clinic.app.ui.common;

import clinic.app.ApplicationContext;
import clinic.app.DataTopic;
import clinic.app.Theme;
import clinic.model.Medicine;
import clinic.service.PageResult;
import java.awt.Dimension;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Searchable medicine selector; popup navigation loads bounded pages on demand. */
public final class MedicinePicker extends JPanel implements clinic.app.ui.common.popup.FormValueComponent {
    private final JComboBox<Object> choices = new JComboBox<>();
    private final MedicinePickerPageLoader pages;
    private final Runnable changed;
    private final JTextField editor;
    private final javax.swing.Timer debounce;
    private final LiveRefresh live;
    private final boolean catalogMode;
    private final MedicinePickerFeedback feedback=new MedicinePickerFeedback();
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
        this.changed = changed;
        pages=new MedicinePickerPageLoader(app,catalogMode);
        setOpaque(false);
        choices.setEditable(true);
        choices.setEditor(new MedicinePickerEditor());
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
        choices.setRenderer(new MedicinePickerRenderer(catalogMode));
        add(choices, java.awt.BorderLayout.NORTH);
        add(feedback,java.awt.BorderLayout.CENTER);
        debounce = new javax.swing.Timer(200, event -> {
            page = 0;
            load();
        });
        debounce.setRepeats(false);
        editor.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                if (!loading && !(choices.getSelectedItem() instanceof MedicinePickerPageOption)) {
                    query = editor.getText().trim();
                    boolean invalidated=false;
                    if (selected != null && !query.equals(selected.getDisplayName())) {
                        selected = null;
                        selectionChanged();
                        invalidated=true;
                    }
                    if(selected==null) {
                        feedback.setAtc("");
                        feedback.setError(query.isBlank()?" ":"Choose a medicine from the list.");
                        firePropertyChange(READY_PROPERTY,null,false);
                        if(!invalidated)changed.run();
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

    public Medicine selected() { return selected; }
    public void showValidationError(String message) {
        if(selected==null)feedback.setError(message==null||message.isBlank()
            ?hasSearchInput()?"Choose a medicine from the list.":" ":message);
    }
    public void focusSearch() { editor.requestFocusInWindow(); }
    public boolean hasSearchInput() { return !query.isBlank(); }
    public void refresh() {
        pages.clear();if(selected!=null){page=0;reconcileSelectionAfterRefresh=true;}load();
    }

    private void choose(Object value) {
        if (loading) return;
        if (value instanceof MedicinePickerPageOption option) {
            loading = true;
            editor.setText(query);
            loading = false;
            page += option == MedicinePickerPageOption.NEXT ? 1 : -1;
            reopenPopup = true;
            load();
        } else if (value instanceof Medicine medicine) {
            if (medicine.getQuantity() <= 0) {
                loading=true;choices.setSelectedItem(null);editor.setText(query);loading=false;
                MedicinePickerStatus.show(choices,"Out of stock — unavailable");
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
        editor.setToolTipText(selected==null?"Type to filter medicines":selected.getName());
        loading = false;
    }
    private void load() {
        int version = ++generation;
        int requestedPage = page;
        String currentQuery = query;
        choices.setEnabled(false);
        MedicinePickerStatus.show(choices,"Loading medicines...");
        pages.load(requestedPage,currentQuery,version,()->version==generation&&live.active(),result->{
            if (version != generation || !live.active()) return;
            showPage(result, currentQuery, requestedPage);
        }, problem -> {
            if (version == generation && live.active()) {
                reconcileSelectionAfterRefresh = false;
                choices.setEnabled(true);
                MedicinePickerStatus.show(choices,"Unable to load medicines. Retry the search.");
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
        if (requestedPage > 0) model.addElement(MedicinePickerPageOption.PREVIOUS);
        result.rows().forEach(model::addElement);
        if (!catalogMode && (long) (requestedPage + 1) * MedicinePickerPageLoader.PAGE_SIZE < result.total()) model.addElement(MedicinePickerPageOption.NEXT);
        choices.setModel(model);
        choices.setSelectedItem(null);
        editor.setText(query);
        loading = false;
        choices.setEnabled(true);
        updateAtc();
        editor.setToolTipText(selected==null?"Type to filter medicines":selected.getName());
        MedicinePickerStatus.show(choices,result.total() + " medicines | page " + (requestedPage + 1));
        if (selectionInvalidated) {
            selectionChanged();
        }
        if (reopenPopup || editor.isFocusOwner()&&!query.isBlank()) {
            reopenPopup = false;
            SwingUtilities.invokeLater(() -> {
                if (isShowing()) choices.setPopupVisible(true);
            });
        }
    }
    @Override
    public void removeNotify() { generation++;debounce.stop();super.removeNotify(); }
    private void selectionChanged() {
        updateAtc();
        feedback.setError(selected==null&&hasSearchInput()?"Choose a medicine from the list.":" ");
        firePropertyChange(READY_PROPERTY,null,isFormReady());changed.run();
    }
    private void updateAtc() {
        if(selected==null){feedback.setAtc("");return;}
        Medicine choice=selected;
        String direct=choice.getProduct().atcLabel();
        feedback.setAtc(direct);
        if(!direct.isBlank())return;
        MedicineCatalogCache.load(catalog->{
            if(selected==null||!selected.getProduct().key().equals(choice.getProduct().key()))return;
            feedback.setAtc(catalog.findByName(choice.getName()).map(entry->entry.product().atcLabel()).orElse(""));
        },problem->{
            if(selected!=null&&selected.getProduct().key().equals(choice.getProduct().key()))
                MedicinePickerStatus.show(choices,"ATC details could not be loaded.");
        });
    }
    @Override public boolean isFormReady(){return selected!=null;}
    @Override public boolean validateForm(){selectionChanged();return isFormReady();}
    @Override public String formValue(){return selected==null?"":selected.getName();}
}
