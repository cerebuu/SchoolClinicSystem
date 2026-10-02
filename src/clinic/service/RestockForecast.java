package clinic.service;

// This class is responsible for estimating how many days of supply remain.
//
// Methods: calculateUsageRate(String medicineName, List<MedicineTransaction> history, int days) returns units per day over the last days.
// estimateDaysRemaining(int currentStock, double usageRate) returns days, or -1 when the rate is 0.
// generateRestockAlert(String medicineName, int currentStock, double usageRate, int warnBelowDays) returns an Alert or null.
// Example: 30 tablets at 5 per day is about 6 days.
public class RestockForecast {
}
