package clinic.service;

// This class is responsible for managing medicine stock, expiry, and restocking.
//
// Holds all Medicine objects in a List<Medicine>.
// Methods: addMedicine(Medicine), restockMedicine(String name, int quantity, LocalDate expiry), getStock(String name) returns the total across batches, getLowStock(int threshold), getExpiringWithin(int days).
// Method: dispense(String name, int qty) takes from the earliest-expiring batch first.
// Stock must never go negative. Throw IllegalArgumentException if there is not enough.
public class Inventory {
}
