package clinic.app;

import clinic.model.Administrator;

/** Dedicated launcher for the authenticated Superadmin interface. */
public final class SuperadminGUI {
    private SuperadminGUI() {}

    public static void main(String[] args) {
        GUI.launch(args, Administrator.class);
    }
}
