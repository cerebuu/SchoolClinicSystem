package clinic.app.ui.consultations;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.popup.FormValueComponent;
import clinic.app.ui.dispense.MedicinePicker;
import clinic.model.Medicine;
import clinic.service.DispenseItem;
import clinic.service.DispenseWorkflow;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

final class ConsultationMedicinePanel extends JPanel implements FormValueComponent {
    private final JPanel rows=new JPanel();
    private final List<MedicinePicker> pickers=new ArrayList<>();
    private final List<JSpinner> quantities=new ArrayList<>();

    ConsultationMedicinePanel(ApplicationContext app) {
        super(new BorderLayout(Theme.GAP,Theme.GAP));
        setOpaque(false);
        rows.setLayout(new BoxLayout(rows,BoxLayout.Y_AXIS));
        rows.setBackground(Theme.WHITE);
        JScrollPane scroll=new JScrollPane(rows,ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        scroll.getViewport().setBackground(Theme.WHITE);
        scroll.setPreferredSize(new Dimension(420,120));
        scroll.getVerticalScrollBar().setUnitIncrement(Theme.SPACE_4);
        add(scroll,BorderLayout.CENTER);
        JButton add=Theme.button("+ Add medicine",false);
        add.addActionListener(event->addRow(app));
        JPanel footer=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));
        footer.setOpaque(false);
        footer.add(add);
        add(footer,BorderLayout.SOUTH);
    }

    private void addRow(ApplicationContext app) {
        JPanel row=new JPanel(new BorderLayout(Theme.GAP,0));
        row.setOpaque(false);
        MedicinePicker picker=new MedicinePicker(app,()->{});
        JSpinner quantity=new JSpinner(new SpinnerNumberModel(1,1,Integer.MAX_VALUE,1));
        quantity.getAccessibleContext().setAccessibleName("Quantity");
        quantity.setPreferredSize(new Dimension(86,Theme.CONTROL_HEIGHT));
        JButton remove=Theme.button("Remove",false);
        row.add(picker,BorderLayout.CENTER);
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.RIGHT,Theme.SPACE_2,0));
        controls.setOpaque(false);
        controls.add(new JLabel("Qty"));
        controls.add(quantity);
        controls.add(remove);
        row.add(controls,BorderLayout.EAST);
        Dimension size=new Dimension(0,Theme.CONTROL_HEIGHT+Theme.SPACE_2);
        row.setMinimumSize(size);
        row.setPreferredSize(size);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE,size.height));
        pickers.add(picker);
        quantities.add(quantity);
        rows.add(row);
        remove.addActionListener(event->{
            int index=pickers.indexOf(picker);
            if(index>=0){pickers.remove(index);quantities.remove(index);}
            rows.remove(row);
            rows.revalidate();
            rows.repaint();
        });
        rows.revalidate();
        rows.repaint();
    }

    List<DispenseItem> items() {
        List<DispenseItem> selected=new ArrayList<>();
        boolean any=pickers.stream().anyMatch(picker->picker.selected()!=null);
        for(int i=0;i<pickers.size();i++) {
            Medicine medicine=pickers.get(i).selected();
            if(medicine==null) {
                if(any)throw new IllegalArgumentException("Choose a medicine or remove its empty row.");
                continue;
            }
            try { quantities.get(i).commitEdit(); }
            catch(java.text.ParseException error) { throw new IllegalArgumentException("Enter a whole-number medicine quantity."); }
            selected.add(new DispenseItem(medicine,(Integer)quantities.get(i).getValue()));
        }
        return selected.isEmpty()?List.of():DispenseWorkflow.merge(selected);
    }

    @Override public String formValue() { return ""; }
}
