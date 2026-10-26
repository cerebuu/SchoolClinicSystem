package clinic.app.ui.common;
import clinic.app.Theme;
import java.awt.*;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
public final class TableRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable owner, Object value,
                boolean selected, boolean focused, int row, int column) {
            String heading = owner.getColumnName(column);
            if (value instanceof Boolean allowed) {
                StatusChip chip = new StatusChip(allowed ? "Allowed" : "Denied",
                    allowed ? StatusChip.Kind.SUCCESS : StatusChip.Kind.DANGER);
                chip.setSelected(selected);
                return chip;
            }
            if ("Status".equalsIgnoreCase(heading) || value instanceof Enum<?>) {
                String text = String.valueOf(value);
                String lower = text.toLowerCase(java.util.Locale.ROOT);
                StatusChip.Kind kind = lower.contains("denied") || lower.contains("failed")
                    || lower.contains("expired") || lower.contains("error")
                    ? StatusChip.Kind.DANGER
                    : lower.contains("pending") || lower.contains("waiting")
                        ? StatusChip.Kind.WARNING
                        : lower.contains("sent") || lower.contains("allowed")
                            || lower.equals("active") || lower.contains("success")
                                ? StatusChip.Kind.SUCCESS : StatusChip.Kind.NEUTRAL;
                StatusChip chip = new StatusChip(text, kind);
                chip.setSelected(selected);
                return chip;
            }
            super.getTableCellRendererComponent(owner, value, selected, focused, row, column);
            setOpaque(true);
            setHorizontalAlignment(value instanceof Number ? RIGHT : LEFT);
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            setBackground(selected ? Theme.BRAND_TINT
                : row % 2 == 0 ? Theme.WHITE : Theme.ZEBRA);
            setForeground(selected ? Theme.BRAND_DARK : Theme.INK);
            setToolTipText(value == null ? null : String.valueOf(value));
            if (focused) {
                setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.FOCUS),
                    BorderFactory.createEmptyBorder(0, 11, 0, 11)));
            }
            return this;
        }
    }
