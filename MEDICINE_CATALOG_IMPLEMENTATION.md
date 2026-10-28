# Medicine catalog implementation

## Workbook inspected

`resources/medicines.xlsx` has 2,068 data rows. The required headers are `Medicine`, `ATC code primary`, `ATC code secondary`, `WHO Model List of Essential Medications`, and `Philippines`. Country columns other than Philippines are ignored. Headers are mapped by name; worksheet order is resolved from the workbook relationship.

| Column | 0 | 1 | Blank / other |
| --- | ---: | ---: | ---: |
| WHO Model List of Essential Medications | 1,654 | 414 | 0 |
| Philippines | 1,549 | 519 | 0 |

The read-only Java smoke demo yields 2,067 unique nonblank medicine names after trimming and deduplication. `1` maps to true. Names are deduplicated without case sensitivity, keeping the first row. Search matches names and either ATC code, ranks prefix before contains, then Philippines availability, then name; results are capped at 50.

## Medicine inputs

- Add medicine batch: free text replaced by the shared catalog picker. Strength and Formulation fields removed.
- Restock flow when no medicine is preselected: uses the same catalog picker. Existing selected-product and selected-batch restock flows keep their exact medicine.
- Dispense: every medicine row uses `clinic.app.ui.common.MedicinePicker` in stock mode; zero-stock choices are hidden.
- New Consultation: uses the same medicine rows and stock picker, including optional-row validation.
- Create notification has no independent medicine input. Its transaction picker continues choosing an existing transaction.

The old picker source in `clinic.app.ui.dispense` was moved into common; there is one implementation. The catalog loads once through AsyncLoader off the EDT and is cached in memory. The picker uses a 200 ms debounce and a scrolling eight-row Swing combo popup with keyboard selection, an inline selection error, and a read-only ATC field. Catalog entries do not create stock rows.

## Storage and legacy behavior

Schema version 12 adds `atc_primary` and `atc_secondary` to `medicine_batches`, `dispensing`, and `inventory_movements`, using `ADD COLUMN IF NOT EXISTS` and empty defaults. New writes and reads use those columns. Retired strength/formulation columns remain in the schema and are not used by application read/write paths. Compatibility tests deliberately populate them to verify they are preserved and never interpreted as ATC codes.

MedicineProduct carries name and both ATC codes through existing ClinicSystem methods. No ClinicSystem signatures changed. Matching, grouping, dispensing and duplicate/refill detection use normalized name identity. Embedded dosage text in legacy names remains part of the name. Legacy ATC defaults are blank. Merging selected catalog stock into an existing batch also saves the chosen ATC metadata. The workbook is never imported into a database catalog table.

Inventory and medication/transaction history display primary ATC codes; tooltips include secondary codes. The picker displays both codes in its read-only field.

## Verification

The requested source build completed with exit code 0:

```sh
find src -name '*.java' > /tmp/srcs.txt
javac -encoding UTF-8 -cp 'lib/*' -d out @/tmp/srcs.txt
```

All src, demo and test sources also compiled together. `sh tools/verify.sh` completed with exit code 0. `sh tools/verify.sh --desktop` completed with exit code 0 using a tiny synthetic catalog for the native Add medicine workflow. The final inline-error adjustment was followed by recompilation and the catalog, selection, and consultation UI checks, all exit code 0.

Actual output excerpts:

```text
PASS: 17 medicine catalog checks.
PASS: 10 medicine selection UI checks.
PASS: 5 consultation medicine UI checks.
PASS: 71 native Swing workflow checks.
Catalog unique medicines=2067
WHO essential=414
Available in Philippines=519
Paracetamol (Acetaminophen) | ATC=N02BE01 | WHO=true | Philippines=true
```

The catalog tests generate a tiny XLSX fixture with shuffled headers, shared and inline strings, a duplicate, a blank row, and non-default worksheet filename. They check missing-header failure, ranking, limits, flags, rejection of unknown text, read-only ATC display, name-only duplicate matching, stock/transaction ATC persistence, and legacy storage compatibility. Existing fixtures now use ATC metadata in place of retired product attributes.

Nothing is blocked. Existing unrelated workspace edits were preserved. No existing production database was opened for these checks; persistent checks use temporary databases.

## Files for this task

### Added

- `MEDICINE_CATALOG_IMPLEMENTATION.md`
- `demo/MedicineCatalogDemo.java`
- `src/clinic/app/ui/common/MedicineCatalogCache.java`
- `src/clinic/app/ui/common/MedicinePicker.java`
- `src/clinic/service/CatalogWorkbook.java`
- `src/clinic/service/MedicineCatalog.java`
- `tests/MedicineCatalogChecks.java`

### Modified

- `src/clinic/app/ui/common/TableRowRenderer.java`
- `src/clinic/app/ui/dispense/DispenseItemRow.java`
- `src/clinic/app/ui/dispense/TransactionsTableModel.java`
- `src/clinic/app/ui/inventory/InventoryCatalog.java`
- `src/clinic/app/ui/inventory/InventoryPanel.java`
- `src/clinic/app/ui/inventory/InventoryTableModel.java`
- `src/clinic/model/Medicine.java`
- `src/clinic/model/MedicineProduct.java`
- `src/clinic/persistence/ClinicRepository.java`
- `src/clinic/persistence/DispenseRepository.java`
- `src/clinic/persistence/Schema.java`
- `src/clinic/service/DispenseQueries.java`
- `src/clinic/service/DispensingService.java`
- `src/clinic/service/Inventory.java`
- `tests/BackendWorkflowRegressionChecks.java`
- `tests/CampusQueryChecks.java`
- `tests/ClinicalAuditChecks.java`
- `tests/ConsultationAdministrationChecks.java`
- `tests/ConsultationMedicineUiChecks.java`
- `tests/DesktopChecks.java`
- `tests/InventoryAuditChecks.java`
- `tests/MedicationHistoryChecks.java`
- `tests/MedicineSelectionUiChecks.java`
- `tests/NotificationCampusChecks.java`
- `tools/verify.cmd`
- `tools/verify.sh`

### Moved from

- `src/clinic/app/ui/dispense/MedicinePicker.java`
