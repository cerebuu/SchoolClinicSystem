package clinic.app;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.UIManager;

public final class Theme {
    public static final Color WHITE = Color.WHITE;
    public static final Color BRAND = new Color(0xD6336C);
    public static final Color BRAND_DARK = new Color(0xA61E4D);
    public static final Color BRAND_TINT = new Color(0xFFF0F6);
    public static final Color BRAND_BORDER = new Color(0xF8C9DB);
    public static final Color INK = new Color(0x1F2933);
    public static final Color MUTED = new Color(0x6B7280);
    public static final Color LINE = new Color(0xDEE3E9);
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
    public static final Color BRAND_GLOW = new Color(0xFFF7FA);
    public static final Color HEADER_TINT = new Color(0xFFF8FB);
    public static final Color ZEBRA = new Color(0xFFFAFC);
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
    public static final int MOTION_HOVER_MS = 160;
    public static final int MOTION_FRAME_MS = 16;
    public static final String SAMPLE_CAMPUS = "Campus 1 (sample)";

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
        UIManager.put("ScrollBar.width", 12);
    }

    public static Font font(int size, int style) {
        return new Font("Segoe UI", style, size);
    }

    public static JButton button(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(font(TYPE_BODY, Font.BOLD));
        button.setForeground(primary ? Color.WHITE : BRAND_DARK);
        button.setBackground(primary ? BRAND : WHITE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? BRAND : BRAND_BORDER),
                BorderFactory.createEmptyBorder(0, 14, 0, 14)));
        button.setFocusPainted(true);
        button.setFocusTraversalKeysEnabled(true);
        button.setOpaque(true);
        button.setRolloverEnabled(true);
        button.setPreferredSize(new java.awt.Dimension(Math.max(100, button.getPreferredSize().width), CONTROL_HEIGHT));
        button.setMinimumSize(new java.awt.Dimension(0, CONTROL_HEIGHT));
        installHover(button, primary ? BRAND : WHITE, primary ? BRAND_DARK : BRAND_GLOW);
        return button;
    }

    private static void installHover(JButton button, Color normal, Color hover) {
        final long[] startedAt = {0L};
        final boolean[] hovering = {false};
        Timer timer = new Timer(MOTION_FRAME_MS, null);
        timer.addActionListener(event -> {
            float progress = Math.min(1f, (System.nanoTime() - startedAt[0]) / 1_000_000f / MOTION_HOVER_MS);
            float amount = hovering[0] ? progress : 1f - progress;
            button.setBackground(blend(normal, hover, amount));
            if (progress >= 1f) timer.stop();
        });
        button.addMouseListener(new MouseAdapter() {
            private void animate(boolean entered) {
                if (!button.isEnabled()) return;
                hovering[0] = entered;
                startedAt[0] = System.nanoTime();
                timer.restart();
            }
            @Override public void mouseEntered(MouseEvent event) { animate(true); }
            @Override public void mouseExited(MouseEvent event) { animate(false); }
        });
    }

    private static Color blend(Color from, Color to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return new Color(
            Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
            Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
            Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t));
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
