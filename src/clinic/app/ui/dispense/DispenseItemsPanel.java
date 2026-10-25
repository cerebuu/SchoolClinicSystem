package clinic.app.ui.dispense;
import clinic.app.*;
import clinic.service.*;
import clinic.app.ui.common.popup.MessagePopup;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

public final class DispenseItemsPanel extends JPanel {
    private final List<DispenseItemRow> rows=new ArrayList<>();
    private final JPanel list=new JPanel();
    private final ApplicationContext app;
    public DispenseItemsPanel(ApplicationContext app) {
        super(new BorderLayout());this.app=app;setOpaque(false);
        list.setLayout(new BoxLayout(list,BoxLayout.Y_AXIS));list.setBackground(Theme.WHITE);
        JScrollPane scroll=new JScrollPane(list);scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setPreferredSize(new Dimension(620,260));add(scroll,BorderLayout.CENTER);
        JButton add=Theme.button("+ Add medicine",false);add.addActionListener(e->addRow());add(add,BorderLayout.SOUTH);addRow();
    }
    private void addRow() {
        DispenseItemRow[] holder=new DispenseItemRow[1];
        holder[0]=new DispenseItemRow(app,()->merge(holder[0]),()->remove(holder[0]));
        rows.add(holder[0]);list.add(holder[0]);list.revalidate();list.repaint();
    }
    private void remove(DispenseItemRow row){rows.remove(row);list.remove(row);list.revalidate();list.repaint();}
    private void merge(DispenseItemRow changed) {
        if(changed==null||changed.medicine.selected()==null)return;
        for(DispenseItemRow row:List.copyOf(rows))if(row!=changed&&row.medicine.selected()!=null
                &&row.medicine.selected().getName().equalsIgnoreCase(changed.medicine.selected().getName())) {
            try {row.quantity.setValue(Math.addExact(row.item().quantity(),changed.item().quantity()));remove(changed);}
            catch(RuntimeException error){MessagePopup.error(this,error);}
            return;
        }
    }
    public List<DispenseItem> items(){return DispenseWorkflow.merge(rows.stream().map(DispenseItemRow::item).toList());}
    public void clear(){for(DispenseItemRow row:List.copyOf(rows))remove(row);addRow();}
    public void refresh(){rows.forEach(row->row.medicine.refresh());}
}
