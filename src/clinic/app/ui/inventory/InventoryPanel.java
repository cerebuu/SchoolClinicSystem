package clinic.app.ui.inventory;
import clinic.app.ApplicationContext;
import clinic.app.ui.common.*;
import clinic.app.ui.common.popup.*;
import clinic.model.*;
import java.awt.BorderLayout;
import java.time.LocalDate;
import java.util.List;
public final class InventoryPanel extends BasePanel {
    public InventoryPanel(ApplicationContext app) {
        super("Medicine inventory", clinic.app.Theme.SAMPLE_CAMPUS);
        PagedTablePanel<Medicine> table=new PagedTablePanel<>(new InventoryTableModel(), app::medicines);
        add(table, BorderLayout.CENTER);
        if(app.current().canAccess(User.RESTOCK)) {
            action("Add medicine", true, ()->FormPopup.show(this, "Add medicine batch", List.of(FormField.text("Medicine"), FormField.text("Quantity"), FormField.text("Expiry (YYYY-MM-DD)"), FormField.optional("Remarks")), v->app.clinic().addMedicine(v.get("Medicine"), Integer.parseInt(v.get("Quantity")), LocalDate.parse(v.get("Expiry (YYYY-MM-DD)")), v.get("Remarks")), table::reload));
            action("Restock", ()-> {
                Medicine m=table.selected();
                if(m==null) {
                    MessagePopup.warning(this, "Select a medicine.");
                    return;
                }
                FormPopup.show(this, "Restock "+m.getName(), List.of(FormField.text("Quantity"), FormField.text("Expiry (YYYY-MM-DD)")), v->app.clinic().restockMedicine(m.getName(), Integer.parseInt(v.get("Quantity")), LocalDate.parse(v.get("Expiry (YYYY-MM-DD)"))), table::reload);
            });
        }
        action("Stock and forecast", ()-> {
            Medicine m=table.selected();
            if(m==null) {
                MessagePopup.warning(this, "Select a medicine.");
                return;
            }
            Actions.run(this, ()->"Available unexpired stock: "+app.clinic().getStock(m.getName())+"\nEstimated days remaining: "+app.clinic().estimateDaysRemaining(m.getName())+" (-1 means no usage history)", message->MessagePopup.info(this, message));
        });
    }
}
