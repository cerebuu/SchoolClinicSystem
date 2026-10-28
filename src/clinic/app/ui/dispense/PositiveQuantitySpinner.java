package clinic.app.ui.dispense;

import java.text.DecimalFormat;
import java.text.ParseException;
import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;

/** A medicine quantity must be an exact positive integer, without partial parsing. */
public final class PositiveQuantitySpinner extends JSpinner {
    public PositiveQuantitySpinner() {
        super(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
        JFormattedTextField input = ((DefaultEditor) getEditor()).getTextField();
        NumberFormatter formatter = new NumberFormatter(new DecimalFormat("0")) {
            @Override public Object stringToValue(String text) throws ParseException {
                String value = text == null ? "" : text.trim();
                try {
                    if (value.matches("[0-9]+")) {
                        int quantity = Integer.parseInt(value);
                        if (quantity > 0) return quantity;
                    }
                } catch (NumberFormatException ignored) { }
                throw new ParseException("Enter a positive whole-number medicine quantity.", 0);
            }
        };
        formatter.setValueClass(Integer.class);
        input.setFormatterFactory(new DefaultFormatterFactory(formatter));
        input.setFocusLostBehavior(JFormattedTextField.PERSIST);
    }

    public int positiveQuantity() {
        try { commitEdit(); }
        catch (ParseException failure) {
            throw new IllegalArgumentException("Enter a positive whole-number medicine quantity.");
        }
        return (Integer) getValue();
    }
}
