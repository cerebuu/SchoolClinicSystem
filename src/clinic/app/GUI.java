import java.awt.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class GUI extends JFrame {

    public GUI() {
        setTitle("UIC School Clinic Management System (SCMS)");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. Sidebar Panel (Left)
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(new Color(245, 245, 245));
        sidebar.setPreferredSize(new Dimension(200, 700));

        String[] menuItems = {
            "Dashboard",
            "Patients",
            "Appointments",
            "Consultations",
            "Medicine Inventory",
            "Verification Requests",
            "Health Analytics",
            "Notifications",
            "Reports",
            "Settings",
        };

        JLabel brandLabel = new JLabel("UIC School Clinic");
        brandLabel.setFont(new Font("Arial", Font.BOLD, 16));
        brandLabel.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));
        sidebar.add(brandLabel);

        for (String item : menuItems) {
            JButton btn = new JButton(item);
            btn.setMaximumSize(new Dimension(200, 40));
            btn.setHorizontalAlignment(SwingConstants.LEFT);
            btn.setBackground(new Color(245, 245, 245));
            btn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
            sidebar.add(btn);
        }
        add(sidebar, BorderLayout.WEST);

        // 2. Main Dashboard Content (Center)
        JPanel mainContent = new JPanel();
        mainContent.setLayout(new BorderLayout(10, 10));
        mainContent.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        mainContent.setBackground(Color.WHITE);

        // Top Row: Key Metrics
        JPanel metricsPanel = new JPanel(new GridLayout(1, 4, 10, 0));
        metricsPanel.add(createCard("Total Patients", "1,248"));
        metricsPanel.add(createCard("Today's Appointments", "34"));
        metricsPanel.add(createCard("Active Consultations", "3"));
        metricsPanel.add(createCard("Medicine Stock Alerts", "12"));
        mainContent.add(metricsPanel, BorderLayout.NORTH);

        // Middle Row: Outbreak & Safety
        JPanel middlePanel = new JPanel(new BorderLayout(10, 0));

        // Outbreak Pattern Detector
        JPanel outbreakPanel = new JPanel(new BorderLayout());
        outbreakPanel.setBorder(
            BorderFactory.createTitledBorder("Outbreak Pattern Detector")
        );
        outbreakPanel.add(
            new JLabel(
                "<html><b>Elevated Flu Symptoms Detected</b><br>15 students from the same department reported flu symptoms within 48 hours.</html>"
            ),
            BorderLayout.CENTER
        );
        middlePanel.add(outbreakPanel, BorderLayout.CENTER);

        // Safety Safeguard
        JPanel safetyPanel = new JPanel(new BorderLayout());
        safetyPanel.setBorder(
            BorderFactory.createTitledBorder("Safety Safeguard")
        );
        safetyPanel.setPreferredSize(new Dimension(300, 100));
        safetyPanel.add(
            new JLabel(
                "<html><font color='red'>INTERACTION WARNING</font><br>Patient Mendoza, Juan<br>Potential allergy risk detected.</html>"
            ),
            BorderLayout.CENTER
        );
        middlePanel.add(safetyPanel, BorderLayout.EAST);

        // Bottom Row: Table & Inventory
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        // Recent Clinic Visits Table
        String[] columns = {
            "Patient",
            "ID Number",
            "Health Concern",
            "Purpose",
            "Status",
        };
        Object[][] data = {
            {
                "Santos, Maria Clara",
                "2021-0012",
                "Flu",
                "Routine Checkup",
                "Consulted",
            },
            {
                "Mendoza, Juan",
                "2022-0441",
                "Sick",
                "Fever / Chills",
                "In Consultation",
            },
            {
                "Cruz, Ana",
                "2020-0099",
                "Healthy",
                "Medical Clearance",
                "Waiting",
            },
        };
        JTable table = new JTable(new DefaultTableModel(data, columns));
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(
            BorderFactory.createTitledBorder("Recent Clinic Visits")
        );
        bottomPanel.add(tableScroll, BorderLayout.CENTER);

        // Inventory Forecasting
        JPanel inventoryPanel = new JPanel(new GridLayout(2, 1));
        inventoryPanel.setBorder(
            BorderFactory.createTitledBorder("Inventory Forecasting")
        );
        inventoryPanel.setPreferredSize(new Dimension(300, 200));
        inventoryPanel.add(
            new JLabel(
                "<html><b>Paracetamol 500mg</b><br>Low Stock (20)</html>"
            )
        );
        inventoryPanel.add(
            new JLabel("<html><b>Cetirizine 10mg</b><br>Stable (140)</html>")
        );
        bottomPanel.add(inventoryPanel, BorderLayout.EAST);

        // Combine Middle and Bottom
        JPanel centerCombined = new JPanel(new BorderLayout(0, 10));
        centerCombined.add(middlePanel, BorderLayout.NORTH);
        centerCombined.add(bottomPanel, BorderLayout.CENTER);

        mainContent.add(centerCombined, BorderLayout.CENTER);

        add(mainContent, BorderLayout.CENTER);
    }

    // Helper method to create metric cards
    private JPanel createCard(String title, String value) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        card.setBackground(Color.WHITE);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("Arial", Font.BOLD, 24));
        valueLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new GUI().setVisible(true);
        });
    }
}
