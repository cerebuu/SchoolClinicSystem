package clinic.app;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
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
    public static final Color BRAND_BORDER = new Color(0xF1B8CD);
    public static final Color INK = new Color(0x1F2933);
    public static final Color MUTED = new Color(0x667585);
    public static final Color LINE = new Color(0xD9E1E8);
    public static final Color SUBTLE = new Color(0xF4F6F8);
    public static final Color NAV = new Color(0x192C3E);
    public static final Color NAV_EDGE = new Color(0x2A4053);
    public static final Color NAV_TEXT = new Color(0xF3F6F9);
    public static final Color NAV_MUTED = new Color(0xB3C2CE);
    public static final Color NAV_HOVER = new Color(0x22394E);
    public static final Color NAV_SELECTED = new Color(0x2B455C);
    public static final Color NAV_FOCUS = new Color(0xF4A8C1);
    public static final Color DISABLED_BG = new Color(0xE9EDF1);
    public static final Color DISABLED_BORDER = new Color(0xD4DBE2);
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
    public static final Color BRAND_GLOW = new Color(0xFFF7FA);
    public static final Color HEADER_TINT = new Color(0xFFF8FB);
    public static final Color ZEBRA = new Color(0xF9FBFD);
    public static final Color FOCUS = new Color(0xC2255C);
    public static final Color WARNING_TINT = new Color(0xFFF4DB);
    public static final Color SHADOW_COLOR = new Color(31, 41, 51, 9);
    public static final int TYPE_CAPTION = 12;
    public static final int TYPE_BODY = 14;
    public static final int TYPE_TITLE = 18;
    public static final int TYPE_PAGE = 24;
    public static final int TYPE_METRIC = 30;
    public static final int SPACE_1 = 4;
    public static final int SPACE_2 = 8;
    public static final int SPACE_3 = 12;
    public static final int SPACE_4 = 16;
    public static final int SPACE_6 = 24;
    public static final int SPACE_8 = 32;
    public static final int RADIUS_CARD = 16;
    public static final int RADIUS_PILL = 999;
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
        UIManager.put("OptionPane.background", WHITE);
        UIManager.put("OptionPane.messageFont", font(TYPE_BODY, Font.PLAIN));
        UIManager.put("OptionPane.buttonFont", font(TYPE_BODY, Font.BOLD));
        UIManager.put("Table.font", font(13, Font.PLAIN));
        UIManager.put("TableHeader.font", font(13, Font.BOLD));
        UIManager.put("Table.rowHeight", 38);
        UIManager.put("ComboBox.background", WHITE);
        UIManager.put("ComboBox.selectionBackground", BRAND_TINT);
        UIManager.put("ComboBox.selectionForeground", INK);
        UIManager.put("Button.select", BRAND_DARK);
        UIManager.put("Button.disabledText", MUTED);
        UIManager.put("Button.focus", FOCUS);
        UIManager.put("ScrollBar.width", 12);
    }

    public static Font font(int size, int style) {
        return new Font("Segoe UI", style, size);
    }

    public static JButton button(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(font(TYPE_BODY, Font.BOLD));
        button.setFocusPainted(false);
        button.setFocusTraversalKeysEnabled(true);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.setRolloverEnabled(true);
        button.setPreferredSize(new java.awt.Dimension(Math.max(100, button.getPreferredSize().width), CONTROL_HEIGHT));
        button.setMinimumSize(new java.awt.Dimension(0, CONTROL_HEIGHT));
        button.getModel().addChangeListener(event -> updateButton(button, primary));
        button.addPropertyChangeListener("enabled", event -> updateButton(button, primary));
        button.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) { updateButton(button, primary); }
            @Override public void focusLost(FocusEvent event) { updateButton(button, primary); }
        });
        updateButton(button, primary);
        return button;
    }

    private static void updateButton(JButton button, boolean primary) {
        Color background;
        Color foreground;
        Color border;
        boolean pressed = button.getModel().isArmed() && button.getModel().isPressed();
        if (!button.isEnabled()) {
            background = DISABLED_BG;
            foreground = MUTED;
            border = DISABLED_BORDER;
        } else if (pressed) {
            background = primary ? new Color(0x7E123B) : BRAND_BORDER;
            foreground = primary ? WHITE : BRAND_DARK;
            border = primary ? new Color(0x7E123B) : BRAND_DARK;
        } else if (button.getModel().isRollover()) {
            background = primary ? BRAND_DARK : BRAND_TINT;
            foreground = primary ? WHITE : BRAND_DARK;
            border = primary ? BRAND_DARK : BRAND_BORDER;
        } else {
            background = primary ? BRAND : WHITE;
            foreground = primary ? WHITE : INK;
            border = primary ? BRAND : LINE;
        }
        if (button.isFocusOwner()) border = FOCUS;
        button.setBackground(background);
        button.setForeground(foreground);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(border, button.isFocusOwner() ? 2 : 1),
            BorderFactory.createEmptyBorder(0, 13, 0, 13)));
    }

    public static void field(JComponent field) {
        field.setFont(font(14, Font.PLAIN));
        field.setPreferredSize(new java.awt.Dimension(Math.max(180, field.getPreferredSize().width), FIELD_HEIGHT));
        field.setMinimumSize(new java.awt.Dimension(0, FIELD_HEIGHT));
        field.setBorder(fieldBorder(LINE));
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) {
                field.setBorder(fieldBorder(FOCUS));
            }
            @Override public void focusLost(FocusEvent event) {
                field.setBorder(fieldBorder(LINE));
            }
        });
    }

    private static javax.swing.border.Border fieldBorder(Color color) {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(color, 1),
            BorderFactory.createEmptyBorder(0, 10, 0, 10));
    }

    public static void surface(JComponent component) {
        component.setBackground(Color.WHITE);
        component.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(LINE),
            BorderFactory.createEmptyBorder(SPACE_4, SPACE_6, SPACE_4, SPACE_6)
        ));
    }

    public static void table(JTable table) {
        table.setFont(font(13, Font.PLAIN));
        table.setForeground(INK);
        table.setGridColor(LINE);
        table.setRowHeight(44);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setIntercellSpacing(new java.awt.Dimension(0, 1));
        table.setSelectionBackground(BRAND_TINT);
        table.setSelectionForeground(BRAND_DARK);
        table.getTableHeader().setFont(font(12, Font.BOLD));
        table.getTableHeader().setForeground(MUTED);
        table.getTableHeader().setBackground(BRAND_TINT);
        table.getTableHeader().setPreferredSize(new java.awt.Dimension(0, 42));
        table.getTableHeader().setReorderingAllowed(false);
        table.setFillsViewportHeight(true);
        table.setFocusable(true);
    }
}
