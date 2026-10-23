package clinic.app;

import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.UIManager;

public final class Theme {
    public static final Color WHITE = Color.WHITE;
    public static final Color BRAND = new Color(0xD6336C);
    public static final Color BRAND_DARK = new Color(0xA61E4D);
    public static final Color BRAND_TINT = new Color(0xFFF0F6);
    public static final Color BRAND_BORDER = new Color(0xF8C9DB);
    public static final Color INK = new Color(0x1F2933);
    public static final Color MUTED = new Color(0x6B7280);
    public static final Color LINE = new Color(0xECEEF1);
    public static final Color SUBTLE = new Color(0xF7F8FA);
    public static final Color DANGER = new Color(0xC92A2A);
    public static final Color DANGER_TINT = new Color(0xFDEDED);
    public static final Color SUCCESS = new Color(0x2F9E44);
    public static final Color SUCCESS_TINT = new Color(0xEBF8EE);
    public static final Color INFO = new Color(0x1C7ED6);
    public static final Color INFO_TINT = new Color(0xE8F3FD);
    public static final int CONTROL_HEIGHT = 42;
    public static final int FIELD_HEIGHT = 44;

    public static final int GAP = 12;
    public static final Color WARNING = new Color(0xF59F00);
    public static javax.swing.border.Border padding() { return BorderFactory.createEmptyBorder(20, 24, 20, 24); }
    public static java.awt.Insets fieldInsets() { return new java.awt.Insets(6, 8, 6, 8); }
    private Theme() {}

    public static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to install the application look and feel", ex);
        }
        UIManager.put("Panel.background", Color.WHITE);
        UIManager.put("Label.foreground", INK);
        UIManager.put("Label.font", font(14, Font.PLAIN));
        UIManager.put("PasswordField.font", font(14, Font.PLAIN));
        UIManager.put("ComboBox.font", font(14, Font.PLAIN));
        UIManager.put("TextArea.font", font(14, Font.PLAIN));
        UIManager.put("Button.font", font(14, Font.BOLD));
        UIManager.put("TextField.font", font(14, Font.PLAIN));
        UIManager.put("Table.font", font(13, Font.PLAIN));
        UIManager.put("TableHeader.font", font(13, Font.BOLD));
        UIManager.put("Table.rowHeight", 38);
    }

    public static Font font(int size, int style) {
        return new Font("Inter", style, size);
    }

    public static JButton button(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(font(13, Font.BOLD));
        button.setForeground(primary ? Color.WHITE : BRAND_DARK);
        button.setBackground(primary ? BRAND : WHITE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? BRAND : BRAND_BORDER),
                BorderFactory.createEmptyBorder(0, 14, 0, 14)));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setPreferredSize(new java.awt.Dimension(Math.max(100, button.getPreferredSize().width), CONTROL_HEIGHT));
        button.setMinimumSize(new java.awt.Dimension(0, CONTROL_HEIGHT));
        return button;
    }

    public static void field(JComponent field) {
        field.setFont(font(14, Font.PLAIN));
        field.setPreferredSize(new java.awt.Dimension(Math.max(180, field.getPreferredSize().width), FIELD_HEIGHT));
        field.setMinimumSize(new java.awt.Dimension(0, FIELD_HEIGHT));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BRAND_BORDER),
                BorderFactory.createEmptyBorder(0, 10, 0, 10)));
    }

    public static void surface(JComponent component) {
        component.setBackground(Color.WHITE);
        component.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(LINE),
            BorderFactory.createEmptyBorder(18, 20, 18, 20)
        ));
    }

    public static void table(JTable table) {
        table.setFont(font(13, Font.PLAIN));
        table.setForeground(INK);
        table.setGridColor(LINE);
        table.setRowHeight(38);
        table.setSelectionBackground(BRAND_TINT);
        table.setSelectionForeground(BRAND_DARK);
        table.getTableHeader().setFont(font(12, Font.BOLD));
        table.getTableHeader().setForeground(MUTED);
        table.getTableHeader().setBackground(SUBTLE);
        table.getTableHeader().setReorderingAllowed(false);
    }
}
