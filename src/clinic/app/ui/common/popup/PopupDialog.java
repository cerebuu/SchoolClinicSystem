package clinic.app.ui.common.popup;

import java.awt.*;
import javax.swing.*;

/** Shared modal dialog setup for forms and confirmations. */
public final class PopupDialog {
    private PopupDialog() {}
    public static JDialog create(Component owner, String title) {
        Window parent=owner instanceof Window w?w:SwingUtilities.getWindowAncestor(owner);
        JDialog dialog=new JDialog(parent,title,Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        return dialog;
    }
    public static void show(Component owner,JDialog dialog) {
        dialog.pack();
        Rectangle bounds=dialog.getGraphicsConfiguration().getBounds();
        Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(dialog.getGraphicsConfiguration());
        dialog.setSize(Math.min(dialog.getWidth(),bounds.width-insets.left-insets.right),
            Math.min(dialog.getHeight(),bounds.height-insets.top-insets.bottom));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
}
