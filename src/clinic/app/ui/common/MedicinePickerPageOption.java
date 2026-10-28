package clinic.app.ui.common;

/** Navigation item shown between bounded medicine result pages. */
enum MedicinePickerPageOption {
    PREVIOUS("← Previous medicines"),
    NEXT("More medicines →");

    private final String label;
    MedicinePickerPageOption(String label) { this.label=label; }
    String label() { return label; }
}
