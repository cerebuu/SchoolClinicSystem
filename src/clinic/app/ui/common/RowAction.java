package clinic.app.ui.common;

import java.util.Objects;

/** One permitted command in a table row's accessible actions menu. */
public record RowAction(String label, Runnable command) {
    public RowAction {
        if (label == null || label.isBlank()) throw new IllegalArgumentException("Action label is required.");
        Objects.requireNonNull(command);
    }
}
