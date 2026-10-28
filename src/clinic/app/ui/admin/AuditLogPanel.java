package clinic.app.ui.admin;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.BasePanel;
import clinic.app.ui.common.PagedTablePanel;
import clinic.service.AuditEntry;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class AuditLogPanel extends BasePanel {
    public AuditLogPanel(ApplicationContext app) {
        super("Audit log");

        JTextField userFilter = filterField("User / nurse ID", "Filter by the actor's user ID");
        JTextField campusFilter = filterField("Campus ID", "Filter by campus ID");
        JTextField actionFilter = filterField("Action", "Filter by action name");
        JTextField dateFilter = filterField("Date", "Enter a date such as 2026-10-28");

        PagedTablePanel<AuditEntry> table = new PagedTablePanel<>(new AuditTableModel(),
            (page, size, quickSearch) -> app.admin.audit(page, size,
                combine(quickSearch,userFilter.getText(),campusFilter.getText(),
                    actionFilter.getText(),dateFilter.getText())),
            "Quick search: user ID / campus / action / date / denied");
        table.refreshOn(app.changes, clinic.app.DataTopic.AUDIT);

        Timer filterDelay = new Timer(250, (ActionEvent event) -> table.setPage(0));
        filterDelay.setRepeats(false);
        DocumentListener filterListener = new DocumentListener() {
            private void changed() {
                filterDelay.restart();
            }

            @Override public void insertUpdate(DocumentEvent event) { changed(); }
            @Override public void removeUpdate(DocumentEvent event) { changed(); }
            @Override public void changedUpdate(DocumentEvent event) { changed(); }
        };
        userFilter.getDocument().addDocumentListener(filterListener);
        campusFilter.getDocument().addDocumentListener(filterListener);
        actionFilter.getDocument().addDocumentListener(filterListener);
        dateFilter.getDocument().addDocumentListener(filterListener);

        JPanel fields = new JPanel(new GridLayout(2, 2, Theme.SPACE_3, Theme.SPACE_2));
        fields.setOpaque(false);
        addFilter(fields, "User / nurse ID", userFilter);
        addFilter(fields, "Campus ID", campusFilter);
        addFilter(fields, "Action", actionFilter);
        addFilter(fields, "Date (YYYY-MM-DD)", dateFilter);

        JLabel hint = new JLabel("Filters update as you type and can be combined with quick search.");
        hint.setFont(Theme.font(Theme.TYPE_CAPTION, java.awt.Font.PLAIN));
        hint.setForeground(Theme.MUTED);
        JButton clear = Theme.button("Clear filters", false);
        clear.addActionListener(event -> {
            userFilter.setText("");
            campusFilter.setText("");
            actionFilter.setText("");
            dateFilter.setText("");
        });
        JPanel filterFooter = new JPanel(new BorderLayout(Theme.GAP, 0));
        filterFooter.setOpaque(false);
        filterFooter.add(hint, BorderLayout.CENTER);
        filterFooter.add(clear, BorderLayout.EAST);

        JPanel filters = new JPanel(new BorderLayout(0, Theme.SPACE_2));
        filters.setOpaque(false);
        filters.add(fields, BorderLayout.NORTH);
        filters.add(filterFooter, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(0, Theme.GAP));
        content.setOpaque(false);
        content.add(filters, BorderLayout.NORTH);
        content.add(table, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
    }

    private static JTextField filterField(String name, String description) {
        JTextField field = new JTextField(12);
        Theme.field(field);
        field.getAccessibleContext().setAccessibleName(name);
        field.getAccessibleContext().setAccessibleDescription(description);
        field.setToolTipText(description);
        return field;
    }

    private static void addFilter(JPanel panel, String labelText, JTextField field) {
        JLabel label = new JLabel(labelText);
        label.setFont(Theme.font(Theme.TYPE_CAPTION, java.awt.Font.BOLD));
        label.setForeground(Theme.INK);
        label.setLabelFor(field);
        JPanel group = new JPanel(new BorderLayout(0, Theme.SPACE_1));
        group.setOpaque(false);
        group.add(label, BorderLayout.NORTH);
        group.add(field, BorderLayout.CENTER);
        panel.add(group);
    }

    static String combine(String quickSearch,String user,String campus,String action,String date) {
        java.util.List<String> tokens=new java.util.ArrayList<>();
        append(tokens,"",quickSearch);
        append(tokens,"user:",user);
        append(tokens,"campus:",campus);
        append(tokens,"action:",action);
        append(tokens,"date:",date);
        return String.join(" ",tokens);
    }

    private static void append(java.util.List<String> tokens,String prefix,String value) {
        if(value==null||value.isBlank())return;
        for(String term:value.trim().split("\\s+"))tokens.add(prefix+term);
    }
}
