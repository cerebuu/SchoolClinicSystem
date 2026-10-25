package clinic.app.ui.common.popup;

import clinic.app.Theme;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;

/** Searchable fixed-choice picker. Selection order follows the supplied choice list. */
public final class MultiSelectField extends JPanel {
    private final Map<String,JCheckBox> choices=new LinkedHashMap<>();
    private final JTextField search=new JTextField();
    private final JPanel rows=new JPanel(), chips=new JPanel();

    public MultiSelectField(List<String> options) {
        super(new BorderLayout(0,Theme.SPACE_2));
        setBackground(Theme.WHITE);
        Theme.field(search);
        search.getAccessibleContext().setAccessibleName("Search symptoms");
        search.setToolTipText("Type to filter choices");
        add(search,BorderLayout.NORTH);
        rows.setLayout(new BoxLayout(rows,BoxLayout.Y_AXIS));
        rows.setBackground(Theme.WHITE);
        for(String option:new LinkedHashSet<>(options)) {
            JCheckBox box=new JCheckBox(option);
            box.setFont(Theme.font(Theme.TYPE_BODY,Font.PLAIN));
            box.setForeground(Theme.INK);
            box.setBackground(Theme.WHITE);
            box.setOpaque(true);
            box.setMaximumSize(new Dimension(Integer.MAX_VALUE,32));
            box.addActionListener(e->refreshChips());
            choices.put(option,box);
            rows.add(box);
        }
        JScrollPane list=scroll(rows);
        list.setPreferredSize(new Dimension(300,112));
        add(list,BorderLayout.CENTER);
        chips.setLayout(new BoxLayout(chips,BoxLayout.Y_AXIS));
        chips.setBackground(Theme.WHITE);
        JScrollPane selected=scroll(chips);
        selected.setPreferredSize(new Dimension(300,72));
        selected.getAccessibleContext().setAccessibleName("Selected symptoms");
        add(selected,BorderLayout.SOUTH);
        setPreferredSize(new Dimension(300,244));
        setMinimumSize(new Dimension(0,244));
        setMaximumSize(new Dimension(Integer.MAX_VALUE,244));
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e){filter();}
            public void removeUpdate(DocumentEvent e){filter();}
            public void changedUpdate(DocumentEvent e){filter();}
        });
    }
    private JScrollPane scroll(JPanel panel) {
        JScrollPane pane=new JScrollPane(panel);
        pane.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        pane.getViewport().setBackground(Theme.WHITE);
        pane.getVerticalScrollBar().setUnitIncrement(24);
        pane.setWheelScrollingEnabled(true);
        return pane;
    }
    public List<String> getSelectedValues() {
        return choices.entrySet().stream().filter(e->e.getValue().isSelected()).map(Map.Entry::getKey).toList();
    }
    private void filter() {
        String query=search.getText().trim().toLowerCase(Locale.ROOT);
        choices.forEach((name,box)->box.setVisible(name.toLowerCase(Locale.ROOT).contains(query)));
        rows.revalidate(); rows.repaint();
    }
    private void refreshChips() {
        chips.removeAll();
        choices.forEach((name,box)->{
            box.setBackground(box.isSelected()?Theme.BRAND_TINT:Theme.WHITE);
            box.setForeground(box.isSelected()?Theme.BRAND_DARK:Theme.INK);
            if(box.isSelected()) {
                JButton chip=new JButton(name+"  ×");
                chip.getAccessibleContext().setAccessibleName("Remove "+name);
                chip.setToolTipText("Remove "+name);
                chip.setFont(Theme.font(Theme.TYPE_BODY,Font.PLAIN));
                chip.setBackground(Theme.BRAND_TINT);
                chip.setForeground(Theme.BRAND_DARK);
                chip.setOpaque(true);
                chip.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BRAND_BORDER),
                    BorderFactory.createEmptyBorder(4,8,4,8)));
                chip.addActionListener(e->{box.setSelected(false);refreshChips();});
                chips.add(chip);
            }
        });
        chips.revalidate(); chips.repaint();
    }
}
