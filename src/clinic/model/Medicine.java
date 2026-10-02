package clinic.model;

// This class is responsible for a medicine with its quantity, batch, and expiration date.
//
// Attributes (private): name, remarks (String), quantity (int), expirationDate, batchDate (LocalDate).
// Constructor: Medicine(String name, int quantity, LocalDate expirationDate, LocalDate batchDate, String remarks).
// Getters for all attributes.
// Methods: reduceQuantity(int amount) throws IllegalArgumentException if amount is below 1 or above stock, addQuantity(int amount), isExpired(), isLowStock(int threshold).
// Quantity may be changed only through these methods (encapsulation).
public class Medicine {
}
