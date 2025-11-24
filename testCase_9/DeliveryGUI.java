package testCase_3;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * DeliveryGUI.java
 *
 * Graphical User Interface for the auction-based delivery system.
 *
 * Display Components:
 * - Master Panel: Shows available and in-delivery packages
 * - Agent Panels: Shows each agent's status, capacity, and current packages
 * - Message Log: Shows system events and auction results
 *
 * The GUI is updated in real-time as the system operates.
 * All updates must be called on the Event Dispatch Thread (EDT).
 */
public class DeliveryGUI extends JFrame {

    // ============================================================
    // GUI COMPONENTS
    // ============================================================

    /**
     * Master panel containing package sections
     */
    private JPanel masterPanel;

    /**
     * Panel for available packages (ready for pickup)
     */
    private JPanel availablePackagesPanel;

    /**
     * Panel for packages being delivered
     */
    private JPanel deliveringPackagesPanel;

    /**
     * Panel containing all agent displays
     */
    private JPanel agentsPanel;

    /**
     * Text area for system messages and logs
     */
    private JTextArea logArea;

    // ============================================================
    // DATA TRACKING
    // ============================================================

    /**
     * Map of agent display components
     * Key: agentName, Value: array of [timeToMasterLabel, deliveryTimeLabel, statusLabel, packageLabel, capacityLabel]
     */
    private Map<String, JLabel[]> agentLabels;

    /**
     * Map of available package panels
     * Key: packageName, Value: JPanel
     */
    private Map<String, JPanel> availablePackagePanels;

    /**
     * Map of delivering package panels
     * Key: packageName, Value: JPanel
     */
    private Map<String, JPanel> deliveringPackagePanels;

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    /**
     * Constructor - Initialize and display GUI
     */
    public DeliveryGUI() {
        agentLabels = new HashMap<>();
        availablePackagePanels = new HashMap<>();
        deliveringPackagePanels = new HashMap<>();

        initializeWindow();
        createMasterPanel();
        createAgentsPanel();
        createLogPanel();

        setVisible(true);
    }

    // ============================================================
    // INITIALIZATION METHODS
    // ============================================================

    /**
     * Initialize main window properties
     */
    private void initializeWindow() {
        setTitle("Auction-Based Delivery System Monitor");
        setSize(1200, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setLocationRelativeTo(null);  // Center on screen
    }

    /**
     * Create master panel showing packages
     * Divided into Available and Delivering sections
     */
    private void createMasterPanel() {
        masterPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        masterPanel.setPreferredSize(new Dimension(240, 0));
        masterPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // === AVAILABLE PACKAGES SECTION ===
        JPanel availableSection = new JPanel(new BorderLayout(5, 5));
        availableSection.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(34, 139, 34), 2),
                "Available for Pickup"));

        availablePackagesPanel = new JPanel();
        availablePackagesPanel.setLayout(new BoxLayout(availablePackagesPanel, BoxLayout.Y_AXIS));
        availablePackagesPanel.setBackground(Color.WHITE);

        JScrollPane availableScroll = new JScrollPane(availablePackagesPanel);
        availableScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        availableSection.add(availableScroll, BorderLayout.CENTER);

        // === BEING DELIVERED SECTION ===
        JPanel deliveringSection = new JPanel(new BorderLayout(5, 5));
        deliveringSection.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(30, 144, 255), 2),
                "Being Delivered"));

        deliveringPackagesPanel = new JPanel();
        deliveringPackagesPanel.setLayout(new BoxLayout(deliveringPackagesPanel, BoxLayout.Y_AXIS));
        deliveringPackagesPanel.setBackground(Color.WHITE);

        JScrollPane deliveringScroll = new JScrollPane(deliveringPackagesPanel);
        deliveringScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        deliveringSection.add(deliveringScroll, BorderLayout.CENTER);

        // Add sections to master panel
        masterPanel.add(availableSection);
        masterPanel.add(deliveringSection);

        add(masterPanel, BorderLayout.WEST);
    }

    /**
     * Create agents panel container
     * Individual agent panels added dynamically via addAgent()
     */
    private void createAgentsPanel() {
        agentsPanel = new JPanel(new GridLayout(1, 7, 10, 10));
        agentsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY, 2),
                "Delivery Agents - Live Status"));
        agentsPanel.setBackground(new Color(245, 245, 245));

        add(agentsPanel, BorderLayout.CENTER);
    }

    /**
     * Create message log panel
     */
    private void createLogPanel() {
        logArea = new JTextArea(10, 100);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logArea.setLineWrap(false);

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.DARK_GRAY, 2),
                "System Messages"));
        logScroll.setPreferredSize(new Dimension(0, 200));
        logScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        add(logScroll, BorderLayout.SOUTH);
    }

    // ============================================================
    // PACKAGE DISPLAY METHODS
    // ============================================================

    /**
     * Add a package to the available packages display
     * Must be called on EDT
     *
     * @param packageName - Name of the package
     * @param deliveryTime - Delivery time in seconds
     */
    public void addAvailablePackage(String packageName, int deliveryTime) {
        assert SwingUtilities.isEventDispatchThread() : "Must be called on EDT";

        JPanel pkgPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        pkgPanel.setMaximumSize(new Dimension(220, 35));
        pkgPanel.setBorder(BorderFactory.createLineBorder(new Color(34, 139, 34), 1));
        pkgPanel.setBackground(new Color(240, 255, 240));

        // Package icon (using text instead of emoji)
        JLabel iconLabel = new JLabel("[PKG]");
        iconLabel.setFont(new Font("Arial", Font.BOLD, 12));
        iconLabel.setForeground(new Color(34, 139, 34));

        // Package info
        JLabel pkgLabel = new JLabel(packageName + ": " + deliveryTime + "s");
        pkgLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        pkgPanel.add(iconLabel);
        pkgPanel.add(pkgLabel);

        availablePackagePanels.put(packageName, pkgPanel);
        availablePackagesPanel.add(pkgPanel);
        availablePackagesPanel.add(Box.createRigidArea(new Dimension(0, 3)));

        availablePackagesPanel.revalidate();
        availablePackagesPanel.repaint();
    }

    /**
     * Remove a package from available packages display
     * Must be called on EDT
     *
     * @param packageName - Name of the package to remove
     */
    public void removeAvailablePackage(String packageName) {
        assert SwingUtilities.isEventDispatchThread() : "Must be called on EDT";

        JPanel pkgPanel = availablePackagePanels.get(packageName);
        if (pkgPanel != null) {
            availablePackagesPanel.remove(pkgPanel);
            availablePackagePanels.remove(packageName);
            availablePackagesPanel.revalidate();
            availablePackagesPanel.repaint();
        }
    }

    /**
     * Add a package to the delivering packages display
     * Must be called on EDT
     *
     * @param packageName - Name of the package
     * @param agentName - Name of delivering agent
     */
    public void addDeliveringPackage(String packageName, String agentName) {
        assert SwingUtilities.isEventDispatchThread() : "Must be called on EDT";

        JPanel pkgPanel = new JPanel(new BorderLayout(5, 5));
        pkgPanel.setMaximumSize(new Dimension(220, 45));
        pkgPanel.setBorder(BorderFactory.createLineBorder(new Color(30, 144, 255), 1));
        pkgPanel.setBackground(new Color(240, 248, 255));

        // Delivery icon (using text)
        JLabel iconLabel = new JLabel("[DELIVERING]");
        iconLabel.setFont(new Font("Arial", Font.BOLD, 10));
        iconLabel.setForeground(new Color(30, 144, 255));
        iconLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Package and agent info
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);
        textPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 5));

        JLabel pkgLabel = new JLabel(packageName);
        pkgLabel.setFont(new Font("Arial", Font.BOLD, 12));

        JLabel agentLabel = new JLabel("-> " + agentName);
        agentLabel.setFont(new Font("Arial", Font.PLAIN, 10));
        agentLabel.setForeground(Color.GRAY);

        textPanel.add(pkgLabel);
        textPanel.add(agentLabel);

        pkgPanel.add(iconLabel, BorderLayout.WEST);
        pkgPanel.add(textPanel, BorderLayout.CENTER);

        deliveringPackagePanels.put(packageName, pkgPanel);
        deliveringPackagesPanel.add(pkgPanel);
        deliveringPackagesPanel.add(Box.createRigidArea(new Dimension(0, 3)));

        deliveringPackagesPanel.revalidate();
        deliveringPackagesPanel.repaint();
    }

    /**
     * Remove a package from delivering packages display
     * Must be called on EDT
     *
     * @param packageName - Name of the package to remove
     */
    public void removeDeliveringPackage(String packageName) {
        assert SwingUtilities.isEventDispatchThread() : "Must be called on EDT";

        JPanel pkgPanel = deliveringPackagePanels.get(packageName);
        if (pkgPanel != null) {
            deliveringPackagesPanel.remove(pkgPanel);
            deliveringPackagePanels.remove(packageName);
            deliveringPackagesPanel.revalidate();
            deliveringPackagesPanel.repaint();
        }
    }

    // ============================================================
    // AGENT DISPLAY METHODS
    // ============================================================

    /**
     * Add an agent to the display
     * Creates a panel showing agent status
     * Must be called on EDT
     *
     * @param agentName - Full display name with priority and capacity (e.g., "Agent1 (P:5, Cap:2)")
     */
    public void addAgent(String agentName) {
        assert SwingUtilities.isEventDispatchThread() : "Must be called on EDT";

        JPanel agentPanel = new JPanel();
        agentPanel.setLayout(new BoxLayout(agentPanel, BoxLayout.Y_AXIS));
        agentPanel.setBackground(Color.WHITE);

        // Determine border color based on priority
        Color borderColor = Color.GRAY;
        if (agentName.contains("P:5")) {
            borderColor = new Color(255, 215, 0);  // Gold
        } else if (agentName.contains("P:4")) {
            borderColor = new Color(192, 192, 192);  // Silver
        } else if (agentName.contains("P:3")) {
            borderColor = new Color(205, 127, 50);  // Bronze
        } else if (agentName.contains("P:2")) {
            borderColor = new Color(176, 196, 222);  // Light Steel Blue
        }

        agentPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(borderColor, 3),
                agentName,
                javax.swing.border.TitledBorder.CENTER,
                javax.swing.border.TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 11)));

        // Create labels for agent information
        JLabel packageLabel = createStyledLabel("Package: None", new Color(240, 248, 255));
        JLabel timeToMasterLabel = createStyledLabel("Time to Master: 0s", Color.WHITE);
        JLabel deliveryTimeLabel = createStyledLabel("Delivery Time: 0s", Color.WHITE);
        JLabel capacityLabel = createStyledLabel("Load: 0/0", Color.WHITE);
        JLabel statusLabel = createStyledLabel("Status: Idle", new Color(200, 200, 200));

        // Make status label more prominent
        statusLabel.setFont(new Font("Arial", Font.BOLD, 11));
        packageLabel.setFont(new Font("Arial", Font.BOLD, 11));

        // Add labels to panel with spacing
        agentPanel.add(packageLabel);
        agentPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        agentPanel.add(capacityLabel);
        agentPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        agentPanel.add(timeToMasterLabel);
        agentPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        agentPanel.add(deliveryTimeLabel);
        agentPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        agentPanel.add(statusLabel);
        agentPanel.add(Box.createVerticalGlue());  // Push content to top

        agentsPanel.add(agentPanel);
        agentsPanel.revalidate();
        agentsPanel.repaint();

        // Store label references: [timeToMaster, deliveryTime, status, package, capacity]
        agentLabels.put(agentName, new JLabel[]{timeToMasterLabel, deliveryTimeLabel, statusLabel, packageLabel, capacityLabel});
    }

    /**
     * Helper method to create styled labels
     *
     * @param text - Label text
     * @param bgColor - Background color
     * @return Styled JLabel
     */
    private JLabel createStyledLabel(String text, Color bgColor) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1),
                BorderFactory.createEmptyBorder(6, 4, 6, 4)
        ));
        label.setOpaque(true);
        label.setBackground(bgColor);
        label.setFont(new Font("Arial", Font.PLAIN, 10));
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        return label;
    }

    /**
     * Update agent time displays
     * Must be called on EDT
     *
     * @param agentName - Agent display name
     * @param timeToMaster - Time to return to master
     * @param deliveryTime - Time remaining for delivery
     */
    public void updateAgentTimes(String agentName, int timeToMaster, int deliveryTime) {
        assert SwingUtilities.isEventDispatchThread() : "Must be called on EDT";

        JLabel[] labels = agentLabels.get(agentName);
        if (labels != null) {
            labels[0].setText("Time to Master: " + timeToMaster + "s");
            labels[1].setText("Delivery Time: " + deliveryTime + "s");

            // Update status based on times
            if (timeToMaster > 0) {
                labels[2].setText("Status: Returning");
                labels[2].setForeground(Color.WHITE);
                labels[2].setBackground(new Color(255, 140, 0));  // Orange
            } else if (deliveryTime > 0) {
                labels[2].setText("Status: Delivering");
                labels[2].setForeground(Color.WHITE);
                labels[2].setBackground(new Color(30, 144, 255));  // Blue
            } else {
                labels[2].setText("Status: Idle");
                labels[2].setForeground(Color.BLACK);
                labels[2].setBackground(new Color(200, 200, 200));  // Gray
            }
        }
    }

    /**
     * Update agent status display
     * Must be called on EDT
     *
     * @param agentName - Agent display name
     * @param status - Status string (IDLE, DELIVERING, RETURNING, TRADING, BIDDING)
     */
    public void updateAgentStatus(String agentName, String status) {
        assert SwingUtilities.isEventDispatchThread() : "Must be called on EDT";

        JLabel[] labels = agentLabels.get(agentName);
        if (labels != null) {
            labels[2].setText("Status: " + status);
            
            // Color code based on status
            switch (status) {
                case "TRADING":
                    labels[2].setForeground(Color.WHITE);
                    labels[2].setBackground(new Color(255, 0, 255));  // Magenta - very visible!
                    break;
                case "BIDDING":
                    labels[2].setForeground(Color.WHITE);
                    labels[2].setBackground(new Color(128, 0, 128));  // Purple
                    break;
                case "DELIVERING":
                    labels[2].setForeground(Color.WHITE);
                    labels[2].setBackground(new Color(30, 144, 255));  // Blue
                    break;
                case "RETURNING":
                    labels[2].setForeground(Color.WHITE);
                    labels[2].setBackground(new Color(255, 140, 0));  // Orange
                    break;
                case "IDLE":
                default:
                    labels[2].setForeground(Color.BLACK);
                    labels[2].setBackground(new Color(200, 200, 200));  // Gray
                    break;
            }
        }
    }

    /**
     * Update agent package display
     * Must be called on EDT
     *
     * @param agentName - Agent display name
     * @param packageName - Package name (or null if none)
     */
    public void updateAgentPackage(String agentName, String packageName) {
        assert SwingUtilities.isEventDispatchThread() : "Must be called on EDT";

        JLabel[] labels = agentLabels.get(agentName);
        if (labels != null) {
            if (packageName != null && !packageName.isEmpty()) {
                labels[3].setText("Package: " + packageName);
                labels[3].setForeground(new Color(0, 100, 0));
            } else {
                labels[3].setText("Package: None");
                labels[3].setForeground(Color.GRAY);
            }
        }
    }

    /**
     * Update agent capacity display
     * Must be called on EDT
     *
     * @param agentName - Agent display name
     * @param currentLoad - Current number of packages
     * @param maxCapacity - Maximum capacity
     */
    public void updateAgentCapacity(String agentName, int currentLoad, int maxCapacity) {
        assert SwingUtilities.isEventDispatchThread() : "Must be called on EDT";

        JLabel[] labels = agentLabels.get(agentName);
        if (labels != null) {
            labels[4].setText("Load: " + currentLoad + "/" + maxCapacity);

            // Color code based on load
            if (currentLoad >= maxCapacity) {
                labels[4].setBackground(new Color(255, 200, 200));  // Red - full
            } else if (currentLoad > 0) {
                labels[4].setBackground(new Color(255, 255, 200));  // Yellow - partial
            } else {
                labels[4].setBackground(Color.WHITE);  // White - empty
            }
        }
    }

    // ============================================================
    // MESSAGE LOG METHODS
    // ============================================================

    /**
     * Add a message to the log area
     * Must be called on EDT
     *
     * @param message - Message text to add
     */
    public void addMessage(String message) {
        assert SwingUtilities.isEventDispatchThread() : "Must be called on EDT";

        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    /**
     * Clear all messages from log
     * Must be called on EDT
     */
    public void clearMessages() {
        assert SwingUtilities.isEventDispatchThread() : "Must be called on EDT";

        logArea.setText("");
    }
}