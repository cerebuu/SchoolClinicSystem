package clinic.app.nav;
import java.util.function.Supplier;
import javax.swing.JPanel;
public record NavItem(String label, boolean allowed, Supplier<JPanel> factory) {
}
