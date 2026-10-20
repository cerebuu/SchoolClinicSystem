import java.awt.*;
// package clinic.app.floyd.gui; // Uncomment and adjust this to match your folder structure in Zed

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class UICClinicDashboard extends JFrame {

    // Theme Colors
    private final Color PRIMARY_PINK = new Color(255, 102, 178);
    private final Color LIGHT_PINK = new Color(255, 204, 229);
    private final Color BACKGROUND_GRAY = new Color(245, 245, 245);
    private final Color DARK_TEXT = new Color(50, 50, 50);

    public UICClinicDashboard() {
        setTitle("UIC School Clinic Management System (SCMS)");
        setSize(1280, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. Top Navigation Bar
        add(createTopNavigation(), BorderLayout.NORTH);

        // 2. Status Report Sidebar
        add(createStatusSidebar(), BorderLayout.WEST);

        // 3. Main Content Area
        add(createMainContent(), BorderLayout.CENTER);
    }

    private JPanel createTopNavigation() {
        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.setBackground(Color.WHITE);

        // Header Row: Title, Search, and Profile
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 10, 20));

        JLabel title = new JLabel("UIC SCMS");
        title.setFont(new Font("Arial", Font.BOLD, 22));
        title.setForeground(PRIMARY_PINK);

        JTextField searchBar = new JTextField(
            "Search patient, ID or medicine..."
        );
        searchBar.setPreferredSize(new Dimension(300, 30));

        JPanel centerSearch = new JPanel(new FlowLayout(FlowLayout.CENTER));
        centerSearch.setBackground(Color.WHITE);
        centerSearch.add(searchBar);

        headerPanel.add(title, BorderLayout.WEST);
        headerPanel.add(centerSearch, BorderLayout.CENTER);

        // Navigation Row
        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        navPanel.setBackground(PRIMARY_PINK);

        String[] menuItems = {
            "Dashboard",
            "Patients",
            "Appointments",
            "Consultations",
            "Medicine Inventory",
            "Vaccination Records",
            "Health Analytics",
            "Reports",
            "Settings",
        };

        for (String item : menuItems) {
            JButton btn = new JButton(item);
            btn.setFont(new Font("Arial", Font.BOLD, 12));
            btn.setForeground(Color.WHITE);
            btn.setBackground(PRIMARY_PINK);
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            navPanel.add(btn);
        }

        topContainer.add(headerPanel, BorderLayout.NORTH);
        topContainer.add(navPanel, BorderLayout.SOUTH);

        return topContainer;
    }

    private JPanel createStatusSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(Color.WHITE);
        sidebar.setPreferredSize(new Dimension(300, 0));
        sidebar.setBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, Color.LIGHT_GRAY)
        );

        JLabel headerLabel = new JLabel("Status Report");
        headerLabel.setFont(new Font("Arial", Font.BOLD, 18));
        headerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerLabel.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));
        sidebar.add(headerLabel);

        // Performance Snapshot based on System Intelligence Signals
        JPanel performancePanel = new JPanel();
        performancePanel.setLayout(
            new BoxLayout(performancePanel, BoxLayout.Y_AXIS)
        );
        performancePanel.setBackground(Color.WHITE);
        performancePanel.setBorder(
            BorderFactory.createTitledBorder("Performance Snapshot")
        );

        performancePanel.add(
            createProgressBar("Innovation / Intelligence", 90)
        );
        performancePanel.add(Box.createRigidArea(new Dimension(0, 10)));
        performancePanel.add(createProgressBar("Automation", 80));
        performancePanel.add(Box.createRigidArea(new Dimension(0, 10)));
        performancePanel.add(createProgressBar("Connected Information", 90));
        performancePanel.add(Box.createRigidArea(new Dimension(0, 10)));
        performancePanel.add(createProgressBar("User Efficiency", 95));

        sidebar.add(performancePanel);
        sidebar.add(Box.createRigidArea(new Dimension(0, 20)));

        // Active Alerts moved to sidebar
        JPanel alertsPanel = new JPanel();
        alertsPanel.setLayout(new BoxLayout(alertsPanel, BoxLayout.Y_AXIS));
        alertsPanel.setBackground(Color.WHITE);
        alertsPanel.setBorder(
            BorderFactory.createTitledBorder("Active System Alerts")
        );

        JPanel outbreakAlert = createAlertCard(
            "Outbreak Pattern Detector",
            "Elevated Flu Symptoms Detected.\n14 students from Engineering reported flu-like symptoms.",
            LIGHT_PINK
        );

        JPanel safetyAlert = createAlertCard(
            "Safety Safeguard",
            "INTERACTION WARNING\nPatient: Mendoza, Juan\nPrescribed Amoxicillin conflicts with Penicillin allergy.",
            new Color(255, 153, 153)
        );

        alertsPanel.add(outbreakAlert);
        alertsPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        alertsPanel.add(safetyAlert);

        sidebar.add(alertsPanel);

        return sidebar;
    }

    private JPanel createProgressBar(String label, int value) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setMaximumSize(new Dimension(280, 40));

        JLabel title = new JLabel(label + " (" + value + "%)");
        title.setFont(new Font("Arial", Font.PLAIN, 12));
        title.setForeground(DARK_TEXT);

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setValue(value);
        progressBar.setForeground(PRIMARY_PINK);
        progressBar.setBackground(BACKGROUND_GRAY);
        progressBar.setBorderPainted(false);
        progressBar.setPreferredSize(new Dimension(280, 10));

        panel.add(title, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createMainContent() {
        // Custom panel to draw a white to pink gradient background
        JPanel contentPanel = new JPanel(new BorderLayout(0, 20)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY
                );

                int w = getWidth();
                int h = getHeight();

                GradientPaint gp = new GradientPaint(
                    0,
                    0,
                    Color.WHITE,
                    0,
                    h,
                    LIGHT_PINK
                );
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, w, h);
            }
        };
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        contentPanel.setOpaque(false); // Ensure gradient shows

        // Metrics Row (Placed at the top using BorderLayout.NORTH)
        JPanel metricsPanel = new JPanel(new GridLayout(1, 4, 15, 0));
        metricsPanel.setOpaque(false); // Make transparent to show gradient
        metricsPanel.add(
            createMetricCard("Total Patients", "1,248", "+12 this week")
        );
        metricsPanel.add(
            createMetricCard("Today's Appointments", "34", "pending")
        );
        metricsPanel.add(
            createMetricCard("Active Consultations", "3", "in progress")
        );
        metricsPanel.add(
            createMetricCard("Medicine Stock Alerts", "12", "items low")
        );

        contentPanel.add(metricsPanel, BorderLayout.NORTH);

        // Recent Visits Table Panel (Placed at the CENTER to fill all remaining space)
        JPanel tableContainer = new JPanel(new BorderLayout(0, 10));
        tableContainer.setOpaque(false);

        JLabel tableLabel = new JLabel("Recent Clinic Visits");
        tableLabel.setFont(new Font("Arial", Font.BOLD, 18));
        tableContainer.add(tableLabel, BorderLayout.NORTH);

        String[] columns = {
            "Patient",
            "ID Number",
            "Department",
            "Purpose",
            "Status",
        };
        Object[][] data = {
            {
                "Santos, Maria Clara",
                "2021-0145",
                "CBA",
                "Routine Checkup",
                "Completed",
            },
            {
                "Mendoza, Juan",
                "2022-0881",
                "CCS",
                "Fever/Chills",
                "In Consultation",
            },
            {
                "Cruz, Ana",
                "2020-3022",
                "Nursing",
                "Medical Clearance",
                "Waiting",
            },
            {
                "Dela Cruz, Pedro",
                "2023-1102",
                "Engineering",
                "Flu Symptoms",
                "Completed",
            },
            { "Rizal, Jose", "2019-0001", "Education", "Headache", "Waiting" },
        };
        JTable table = new JTable(new DefaultTableModel(data, columns));
        table.setRowHeight(35);
        table.getTableHeader().setFont(new Font("Arial", Font.BOLD, 14));
        table.getTableHeader().setBackground(LIGHT_PINK);
        table.getTableHeader().setOpaque(false);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBackground(Color.WHITE);
        tableScroll.getViewport().setBackground(Color.WHITE);

        tableContainer.add(tableScroll, BorderLayout.CENTER);

        contentPanel.add(tableContainer, BorderLayout.CENTER);

        return contentPanel;
    }

    private JPanel createMetricCard(
        String title,
        String value,
        String subtext
    ) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
            )
        );

        JLabel titleLbl = new JLabel(title);
        titleLbl.setForeground(DARK_TEXT);
        JLabel valLbl = new JLabel(value);
        valLbl.setFont(new Font("Arial", Font.BOLD, 28));
        valLbl.setForeground(PRIMARY_PINK);
        JLabel subLbl = new JLabel(subtext);
        subLbl.setForeground(Color.GRAY);
        subLbl.setFont(new Font("Arial", Font.ITALIC, 11));

        card.add(titleLbl);
        card.add(Box.createRigidArea(new Dimension(0, 8)));
        card.add(valLbl);
        card.add(Box.createRigidArea(new Dimension(0, 5)));
        card.add(subLbl);

        return card;
    }

    private JPanel createAlertCard(
        String title,
        String message,
        Color bgColor
    ) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(bgColor);
        card.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        card.setMaximumSize(new Dimension(280, 100));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Arial", Font.BOLD, 12));

        JTextArea msgArea = new JTextArea(message);
        msgArea.setWrapStyleWord(true);
        msgArea.setLineWrap(true);
        msgArea.setOpaque(false);
        msgArea.setEditable(false);
        msgArea.setFont(new Font("Arial", Font.PLAIN, 11));

        card.add(titleLbl, BorderLayout.NORTH);
        card.add(msgArea, BorderLayout.CENTER);

        return card;
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            new UICClinicDashboard().setVisible(true);
        });
    }
}
