package clinic.app.ui.common;

import clinic.app.Theme;
import clinic.model.Medicine;
import java.awt.Component;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.JLabel;

/** Shared result-row appearance for medicine pickers. */
final class MedicinePickerRenderer extends DefaultListCellRenderer {
    private final boolean catalogMode;
    MedicinePickerRenderer(boolean catalogMode) { this.catalogMode=catalogMode; }

    @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,
            boolean active,boolean focus) {
        JLabel label=(JLabel)super.getListCellRendererComponent(list,value,index,active,focus);
        if(value instanceof Medicine medicine) {
            String stock=catalogMode?"":medicine.getQuantity()==0?"Out of stock":medicine.getQuantity()
                +" in stock · earliest expiry "+medicine.getExpirationDate();
            String atc=medicine.getProduct().atcLabel();
            String detail=(atc.isBlank()?"":"ATC "+escape(atc)+(stock.isBlank()?"":" · "))+escape(stock);
            label.setText("<html>"+escape(medicine.getName())+"<br><font color='#"
                +String.format("%06x",Theme.MUTED.getRGB()&0xffffff)+"'><small>"
                +detail+"</small></font></html>");
            label.setEnabled(medicine.getQuantity()>0);
        } else if(value instanceof MedicinePickerPageOption option)label.setText(option.label());
        else label.setText("Select medicine");
        label.setBackground(active?Theme.BRAND_TINT:Theme.WHITE);label.setForeground(Theme.INK);
        return label;
    }

    private static String escape(String value) {
        return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
