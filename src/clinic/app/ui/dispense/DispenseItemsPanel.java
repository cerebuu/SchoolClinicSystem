package clinic.app.ui.dispense;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.popup.FormValueComponent;
import clinic.app.ui.common.popup.MessagePopup;
import clinic.app.ui.dashboard.WheelForwarder;
import clinic.model.Patient;
import clinic.service.DispenseItem;
import clinic.service.DispenseWorkflow;

public final class DispenseItemsPanel extends JPanel implements FormValueComponent {
    private final List<DispenseItemRow> rows = new ArrayList<>();
    private final JPanel list = new JPanel();
    private final JScrollPane scroll;
    private final ApplicationContext app;
    private final boolean optionalRows;
    private final JTextArea allergyNotice=new JTextArea(2,32);
    private Patient patient;

    public DispenseItemsPanel(ApplicationContext app) {
        this(app, false);
    }

    /** Optional rows are used by consultations; blank rows are ignored after inline validation. */
    public DispenseItemsPanel(ApplicationContext app, boolean optionalRows) {
        super(new BorderLayout(Theme.GAP, Theme.GAP));
        this.app = app;
        this.optionalRows = optionalRows;
        setOpaque(false);
        allergyNotice.setEditable(false);allergyNotice.setFocusable(false);allergyNotice.setLineWrap(true);
        allergyNotice.setName("recorded-allergy-notice");
        allergyNotice.setWrapStyleWord(true);allergyNotice.setOpaque(true);allergyNotice.setBackground(Theme.WARNING_TINT);
        allergyNotice.setForeground(Theme.MUTED);allergyNotice.setFont(Theme.font(Theme.TYPE_CAPTION,java.awt.Font.PLAIN));
        allergyNotice.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_2,Theme.SPACE_2,Theme.SPACE_2,Theme.SPACE_2));
        allergyNotice.setText("Select a patient to check allergies.");
        add(allergyNotice,BorderLayout.NORTH);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(Theme.WHITE);
        scroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        scroll.getViewport().setBackground(Theme.WHITE);
        scroll.getVerticalScrollBar().setUnitIncrement(Theme.SPACE_4);
        scroll.setWheelScrollingEnabled(true);
        add(scroll, BorderLayout.CENTER);
        JButton add = Theme.button("+ Add medicine", false);
        add.addActionListener(event -> addRow(false));
        add(add, BorderLayout.SOUTH);
        addRow(true);
    }

    private void addRow(boolean initialRow) {
        DispenseItemRow[] holder = new DispenseItemRow[1];
        holder[0] = new DispenseItemRow(app, () -> merge(holder[0]), this::updateListHeight,
            () -> remove(holder[0]), optionalRows,initialRow);
        holder[0].setPatient(patient);
        rows.add(holder[0]);
        list.add(holder[0]);
        updateListHeight();
        if(!initialRow)javax.swing.SwingUtilities.invokeLater(()->{
            list.doLayout();
            java.awt.Rectangle bounds=holder[0].getBounds();
            list.scrollRectToVisible(bounds);
            holder[0].medicine.focusSearch();
        });
    }

    private void remove(DispenseItemRow row) {
        rows.remove(row);
        list.remove(row);
        updateListHeight();
    }

    private void updateListHeight() {
        int contentHeight = rows.stream().mapToInt(DispenseItemRow::rowHeight).sum();
        list.setPreferredSize(new Dimension(0, contentHeight));
        list.setMinimumSize(new Dimension(0, contentHeight));
        int viewportHeight=Math.max(DispenseLayout.ITEM_ROW_HEIGHT,
            Math.min(contentHeight,maxRowsViewportHeight()));
        scroll.setPreferredSize(new Dimension(0, viewportHeight));
        scroll.setMinimumSize(new Dimension(0, DispenseLayout.ITEM_ROW_HEIGHT));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE,viewportHeight));
        int noticeHeight=allergyNotice.getPreferredSize().height+Theme.GAP;
        int sectionHeight = viewportHeight + Theme.CONTROL_HEIGHT + Theme.GAP + Theme.SPACE_1+noticeHeight;
        setMinimumSize(new Dimension(0, sectionHeight));
        setPreferredSize(new Dimension(0, sectionHeight));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, sectionHeight));
        list.revalidate();
        revalidate();
        repaint();
    }

    private static int maxRowsViewportHeight() {
        int screenHeight=900;
        if(!GraphicsEnvironment.isHeadless())screenHeight=Toolkit.getDefaultToolkit().getScreenSize().height;
        return Math.max(DispenseLayout.ITEM_ROW_HEIGHT,Math.min(480,(int)(screenHeight*.4)));
    }

    @Override public void addNotify() {
        super.addNotify();
        javax.swing.SwingUtilities.invokeLater(()->{
            if(!isDisplayable())return;
            Component ancestor=javax.swing.SwingUtilities.getAncestorOfClass(JScrollPane.class,this);
            if(ancestor instanceof JScrollPane page)WheelForwarder.install(page);
        });
    }

    private void merge(DispenseItemRow changed) {
        if (changed == null || changed.medicine.selected() == null) return;
        for (DispenseItemRow row : List.copyOf(rows)) {
            if (row != changed && row.medicine.selected() != null
                && row.medicine.selected().getProduct().key()
                    .equals(changed.medicine.selected().getProduct().key())) {
                try {
                    row.quantity.setValue(Math.addExact(row.item().quantity(), changed.item().quantity()));
                    remove(changed);
                } catch (RuntimeException error) {
                    MessagePopup.error(this, error);
                }
                return;
            }
        }
    }

    public List<DispenseItem> items() {
        if (optionalRows) {
            if (!validateOptionalRows()) throw new IllegalArgumentException("Fix the highlighted medicine row.");
            List<DispenseItem> selected = rows.stream().map(DispenseItemRow::optionalItem)
                .filter(java.util.Objects::nonNull).toList();
            return selected.isEmpty() ? List.of() : DispenseWorkflow.merge(selected);
        }
        return DispenseWorkflow.merge(rows.stream().map(DispenseItemRow::item).toList());
    }

    @Override public String formValue() { return ""; }

    @Override public boolean validateForm() {
        return validateRows();
    }

    public boolean validateOptionalRows() {
        return validateRows();
    }

    private boolean validateRows() {
        boolean valid = true;
        for (DispenseItemRow row : rows) valid = row.validate(optionalRows) && valid;
        if (!valid) {
            DispenseItemRow firstInvalid = rows.stream().filter(row -> !row.validationError().isBlank())
                .findFirst().orElse(null);
            if (firstInvalid != null) {
                firstInvalid.scrollValidationErrorIntoView(list);
                javax.swing.SwingUtilities.invokeLater(firstInvalid::focusInvalidField);
            }
        }
        return valid;
    }

    public void setPatient(Patient value) {
        patient=value;
        if(value==null)allergyNotice.setText("Select a patient to check allergies.");
        else if(value.getAllergies().isEmpty())allergyNotice.setText("No recorded allergies.");
        else allergyNotice.setText("Recorded allergies: "+String.join(", ",value.getAllergies())
            +". The app does not check medicine ingredients.");
        allergyNotice.setForeground(value!=null&&!value.getAllergies().isEmpty()?Theme.WARNING:Theme.MUTED);
        rows.forEach(row->row.setPatient(value));
        updateListHeight();
    }

    public Patient patient() { return patient; }

    public String validationErrors() {
        return rows.stream().map(DispenseItemRow::validationError)
            .filter(message -> !message.isBlank()).toList().toString();
    }

    public void clear() {
        for (DispenseItemRow row : List.copyOf(rows)) remove(row);
        addRow(true);
    }

    public void refresh() {
        rows.forEach(row -> row.medicine.refresh());
    }

    public int itemCount() {
        return rows.size();
    }

    JScrollPane rowsScrollPane() { return scroll; }
    int rowsContentHeight() { return list.getPreferredSize().height; }

    public int itemViewportHeight() {
        return scroll.getViewport().getExtentSize().height;
    }

    public int visibleItemRows() {
        scroll.getViewport().doLayout();
        list.doLayout();
        java.awt.Rectangle visible = scroll.getViewport().getViewRect();
        int count = 0;
        for (DispenseItemRow row : rows) {
            if (row.getBounds().intersects(visible)) count++;
        }
        return count;
    }

    public String itemBounds() {
        return rows.stream().map(row -> row.getBounds().toString()).toList().toString()
            + " view=" + scroll.getViewport().getViewRect();
    }
}
