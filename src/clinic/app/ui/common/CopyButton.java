package clinic.app.ui.common;

import clinic.app.Theme;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.Timer;

/** Creates consistently styled copy buttons with brief success feedback. */
public final class CopyButton {
    private CopyButton() {}

    public static JButton create(String label,String name,String value) {
        return create(label,name,value,ClipboardSupport::copyText);
    }

    public static JButton create(String label,String name,String value,Consumer<String> copier) {
        JButton button=Theme.button(label,false);
        button.setName(name);
        button.setToolTipText(label);
        button.getAccessibleContext().setAccessibleName(label);
        String original=label;
        Timer feedback=new Timer(1500,event->{
            button.setText(original);
            ((Timer)event.getSource()).stop();
        });
        feedback.setRepeats(false);
        button.addActionListener(event->{
            copier.accept(Objects.requireNonNull(value));
            button.setText("Copied");
            feedback.restart();
        });
        return button;
    }
}
