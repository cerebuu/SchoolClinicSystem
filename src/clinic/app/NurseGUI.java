package clinic.app;

import clinic.model.SchoolNurse;

/** Dedicated launcher for the authenticated school nurse interface. */
public final class NurseGUI {
    private NurseGUI() {}

    public static void main(String[] args) {
        GUI.launch(args, SchoolNurse.class);
    }
}
