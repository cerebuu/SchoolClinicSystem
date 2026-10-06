package clinic.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import clinic.model.*;

// This class is responsible for checking user IDs and passwords and tracking who is logged in.
public class Login {

    private final Map<String, User> users = new HashMap<>();
    private final Map<String, String> passwords = new HashMap<>();
    private User currentUser;

    /** Register a user with a password. Throws if the ID is already taken. */
    public void addUser(User user, String password) {
        if (user == null || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("User and password are required.");
        }
        if (users.containsKey(user.getUserId())) {
            throw new IllegalArgumentException("User ID already exists: " + user.getUserId());
        }
        users.put(user.getUserId(), user);
        passwords.put(user.getUserId(), password);
    }

    /** Returns the user if the ID and password match, otherwise empty. */
    public Optional<User> login(String userId, String password) {
        if (userId == null || password == null) {
            return Optional.empty();
        }
        User user = users.get(userId.trim());
        if (user == null || !password.equals(passwords.get(user.getUserId()))) {
            return Optional.empty();
        }
        currentUser = user;
        return Optional.of(user);
    }

    public void logout() {
        currentUser = null;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    /** Test accounts for development. Replace or extend in SampleData later. */
    public static Login withSampleUsers() {
        Login login = new Login();
        login.addUser(new SchoolNurse("N001", "Nurse Ann"), "nurse123");
        login.addUser(new NurseAssistant("A001", "Assistant Bo"), "assist123");
        login.addUser(new Administrator("AD001", "Admin Cy"), "admin123");
        login.addUser(new Doctor("D001", "Dr. Di", "Pediatrics"), "doctor123");
        return login;
    }
}
