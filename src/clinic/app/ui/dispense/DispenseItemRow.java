package clinic.app.ui.dispense;
import clinic.app.*;
import clinic.service.DispenseItem;
import java.awt.*;
import javax.swing.*;

final class DispenseItemRow extends JPanel {
    final MedicinePicker medicine;
    final JSpinner quantity=new JSpinner(new SpinnerNumberModel(1,1,Integer.MAX_VALUE,1));
    private final JLabel stock=new JLabel("Available: —");
    DispenseItemRow(ApplicationContext app,Runnable changed,Runnable remove) {
        super(new BorderLayout(Theme.GAP,Theme.GAP));setOpaque(false);
        medicine=new MedicinePicker(app,()->{updateStock();changed.run();});
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT));controls.setOpaque(false);
        quantity.getAccessibleContext().setAccessibleName("Quantity");quantity.setPreferredSize(new Dimension(90,Theme.FIELD_HEIGHT));
        JButton delete=Theme.button("Remove",false);delete.addActionListener(e->remove.run());
        controls.add(new JLabel("Quantity"));controls.add(quantity);controls.add(stock);controls.add(delete);
        add(medicine,BorderLayout.CENTER);add(controls,BorderLayout.SOUTH);
        setBorder(BorderFactory.createMatteBorder(0,0,1,0,Theme.LINE));
    }
    private void updateStock(){stock.setText("Available: "+(medicine.selected()==null?"—":medicine.selected().getQuantity()));}
    DispenseItem item() {
        try{quantity.commitEdit();}catch(java.text.ParseException ex){throw new IllegalArgumentException("Enter a positive whole quantity.");}
        if(medicine.selected()==null)throw new IllegalArgumentException("Choose a medicine for every row.");
        return new DispenseItem(medicine.selected().getName(),(Integer)quantity.getValue());
    }
}
