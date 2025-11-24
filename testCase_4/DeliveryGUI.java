package testCase_6;
import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class DeliveryGUI extends JFrame {
    private JPanel masterPanel;
    private JPanel agentsPanel;
    private JTextArea logArea;
    private Map<String, JLabel[]> agentLabels = new HashMap<>();
    private Map<String, JButton[]> agentButtons = new HashMap<>();
    private JPanel masterBoxesPanel;
    private Map<String, JPanel> packagePanels = new HashMap<>();

    public DeliveryGUI() {
        setTitle("Delivery System Monitor - Multi-Agent Collaboration");
        setSize(1100, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // === Master Panel ===
        masterPanel = new JPanel(new BorderLayout(10, 10));
        masterPanel.setBorder(BorderFactory.createTitledBorder("Master Agent - Available Packages"));
        masterPanel.setPreferredSize(new Dimension(200, 0));

        masterBoxesPanel = new JPanel();
        masterBoxesPanel.setLayout(new BoxLayout(masterBoxesPanel, BoxLayout.Y_AXIS));
        JScrollPane masterScroll = new JScrollPane(masterBoxesPanel);
        masterPanel.add(masterScroll, BorderLayout.CENTER);

        add(masterPanel, BorderLayout.WEST);

        // === Agents Panel ===
        agentsPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        agentsPanel.setBorder(BorderFactory.createTitledBorder("Delivery Agents - Live Status"));
        add(agentsPanel, BorderLayout.CENTER);

        // === Log Area ===
        logArea = new JTextArea(10, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("System Messages & Events"));
        add(logScroll, BorderLayout.SOUTH);

        // === Control Panel ===
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        controlPanel.setBorder(BorderFactory.createTitledBorder("System Controls"));

        JButton pauseButton = new JButton("⏸️ PAUSE SYSTEM");
        pauseButton.setBackground(new Color(255, 200, 100));
        pauseButton.setForeground(Color.BLACK);
        pauseButton.setFont(new Font("Arial", Font.BOLD, 14));
        pauseButton.setFocusPainted(false);
        pauseButton.setPreferredSize(new Dimension(180, 40));

        JButton resumeButton = new JButton("▶️ RESUME SYSTEM");
        resumeButton.setBackground(new Color(100, 200, 100));
        resumeButton.setForeground(Color.WHITE);
        resumeButton.setFont(new Font("Arial", Font.BOLD, 14));
        resumeButton.setFocusPainted(false);
        resumeButton.setPreferredSize(new Dimension(180, 40));
        resumeButton.setEnabled(false);

        pauseButton.addActionListener(e -> {
            systemPaused = true;
            pauseButton.setEnabled(false);
            resumeButton.setEnabled(true);
            addMessage("⏸️ ========== SYSTEM PAUSED ==========");
        });

        resumeButton.addActionListener(e -> {
            systemPaused = false;
            resumeButton.setEnabled(false);
            pauseButton.setEnabled(true);
            addMessage("▶️ ========== SYSTEM RESUMED ==========");
        });

        controlPanel.add(pauseButton);
        controlPanel.add(resumeButton);

        add(controlPanel, BorderLayout.NORTH);

        setVisible(true);
    }

    private boolean systemPaused = false;

    public boolean isSystemPaused() {
        return systemPaused;
    }

    public void addMasterPackage(String packageName, int deliveryTime) {
        SwingUtilities.invokeLater(() -> {
            JPanel pkgPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            pkgPanel.setMaximumSize(new Dimension(180, 40));
            pkgPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));

            JLabel boxIcon = new JLabel("📦");
            boxIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));

            JLabel pkgLabel = new JLabel(" " + packageName + ": " + deliveryTime + "s");
            pkgLabel.setFont(new Font("Arial", Font.BOLD, 12));

            pkgPanel.add(boxIcon);
            pkgPanel.add(pkgLabel);

            packagePanels.put(packageName, pkgPanel);

            masterBoxesPanel.add(pkgPanel);
            masterBoxesPanel.add(Box.createRigidArea(new Dimension(0, 5)));
            masterBoxesPanel.revalidate();
            masterBoxesPanel.repaint();
        });
    }

    public synchronized void removeMasterPackage(String packageName) {
        SwingUtilities.invokeLater(() -> {
            JPanel pkgPanel = packagePanels.get(packageName);
            if (pkgPanel != null) {
                masterBoxesPanel.remove(pkgPanel);
                packagePanels.remove(packageName);
                masterBoxesPanel.revalidate();
                masterBoxesPanel.repaint();
            }
        });
    }

    public void addAgent(String agentName) {
        JPanel agentPanel = new JPanel();
        agentPanel.setLayout(new BoxLayout(agentPanel, BoxLayout.Y_AXIS));
        agentPanel.setBorder(BorderFactory.createTitledBorder(agentName));

        JLabel currentPkgLabel = new JLabel("Package: None", SwingConstants.CENTER);
        JLabel timeToMasterLabel = new JLabel("Time to Master: 0s", SwingConstants.CENTER);
        JLabel deliveryTimeLabel = new JLabel("Delivery Time: 0s", SwingConstants.CENTER);
        JLabel consecutiveLabel = new JLabel("Consecutive: 0", SwingConstants.CENTER);
        JLabel statusLabel = new JLabel("Status: Idle", SwingConstants.CENTER);

        // Style labels
        currentPkgLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                BorderFactory.createEmptyBorder(8, 5, 8, 5)
        ));
        currentPkgLabel.setOpaque(true);
        currentPkgLabel.setBackground(new Color(240, 248, 255));
        currentPkgLabel.setFont(new Font("Arial", Font.BOLD, 12));

        timeToMasterLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                BorderFactory.createEmptyBorder(8, 5, 8, 5)
        ));
        timeToMasterLabel.setOpaque(true);
        timeToMasterLabel.setBackground(Color.WHITE);

        deliveryTimeLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                BorderFactory.createEmptyBorder(8, 5, 8, 5)
        ));
        deliveryTimeLabel.setOpaque(true);
        deliveryTimeLabel.setBackground(Color.WHITE);

        consecutiveLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                BorderFactory.createEmptyBorder(8, 5, 8, 5)
        ));
        consecutiveLabel.setOpaque(true);
        consecutiveLabel.setBackground(new Color(255, 250, 205));
        consecutiveLabel.setFont(new Font("Arial", Font.BOLD, 11));

        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                BorderFactory.createEmptyBorder(8, 5, 8, 5)
        ));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(200, 200, 200));
        statusLabel.setFont(new Font("Arial", Font.BOLD, 12));

        // Control button - only one now
        JButton triggerButton = new JButton("💥 Trigger Failure");
        triggerButton.setBackground(new Color(255, 100, 100));
        triggerButton.setForeground(Color.WHITE);
        triggerButton.setFocusPainted(false);
        triggerButton.setFont(new Font("Arial", Font.BOLD, 10));

        JButton dummyButton = new JButton(); // Placeholder for array compatibility
        dummyButton.setVisible(false);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 1, 5, 5));
        buttonPanel.add(triggerButton);

        agentPanel.add(currentPkgLabel);
        agentPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        agentPanel.add(timeToMasterLabel);
        agentPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        agentPanel.add(deliveryTimeLabel);
        agentPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        agentPanel.add(consecutiveLabel);
        agentPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        agentPanel.add(statusLabel);
        agentPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        agentPanel.add(buttonPanel);

        agentsPanel.add(agentPanel);
        agentsPanel.revalidate();
        agentsPanel.repaint();

        agentLabels.put(agentName, new JLabel[]{timeToMasterLabel, deliveryTimeLabel, statusLabel, currentPkgLabel, consecutiveLabel});
        agentButtons.put(agentName, new JButton[]{triggerButton, dummyButton});

        // Connect buttons to agents after 2 seconds (wait for JADE agents to be created)
        Timer connectionTimer = new Timer(2000, e -> {
            connectButtonsToAgent(agentName, triggerButton);
            ((Timer)e.getSource()).stop();
        });
        connectionTimer.start();
    }

    private void connectButtonsToAgent(String agentName, JButton triggerButton) {
        // This method will be called to connect buttons to actual agent behavior
        // We'll implement this through MasterAgent
        triggerButton.addActionListener(e -> {
            if (MasterAgent.gui != null) {
                triggerAgentFailure(agentName);
            }
        });
    }

    public void triggerAgentFailure(String agentName) {
        addMessage("💥 Manual failure trigger requested for " + agentName);
        // This will be called from MasterAgent to trigger failure
    }

    public void updateAgentTimes(String agentName, int timeToMaster, int deliveryTime) {
        SwingUtilities.invokeLater(() -> {
            JLabel[] labels = agentLabels.get(agentName);
            if (labels != null) {
                labels[0].setText("Time to Master: " + timeToMaster + "s");
                labels[1].setText("Delivery Time: " + deliveryTime + "s");

                if (timeToMaster > 0) {
                    labels[2].setText("Status: Returning to Master");
                    labels[2].setForeground(Color.WHITE);
                    labels[2].setBackground(new Color(255, 140, 0)); // Orange
                } else if (deliveryTime > 0) {
                    labels[2].setText("Status: Delivering");
                    labels[2].setForeground(Color.WHITE);
                    labels[2].setBackground(new Color(30, 144, 255)); // Blue
                } else {
                    labels[2].setText("Status: Idle (At Master)");
                    labels[2].setForeground(Color.WHITE);
                    labels[2].setBackground(new Color(128, 128, 128)); // Gray
                }
            }
        });
    }

    public void updateAgentPackage(String agentName, String packageName) {
        SwingUtilities.invokeLater(() -> {
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
        });
    }

    public void updateConsecutiveDeliveries(String agentName, int count) {
        SwingUtilities.invokeLater(() -> {
            JLabel[] labels = agentLabels.get(agentName);
            if (labels != null && labels.length > 4) {
                labels[4].setText("Consecutive: " + count);
                if (count >= 3) {
                    labels[4].setBackground(new Color(255, 200, 200));
                    labels[4].setForeground(new Color(150, 0, 0));
                } else {
                    labels[4].setBackground(new Color(255, 250, 205));
                    labels[4].setForeground(Color.BLACK);
                }
            }
        });
    }

    public void updateAgentStatus(String agentName, String status) {
        SwingUtilities.invokeLater(() -> {
            JLabel[] labels = agentLabels.get(agentName);
            if (labels != null) {
                labels[2].setText("Status: " + status);

                // Update color based on status
                if (status.contains("STRANDED") || status.contains("FAILED")) {
                    labels[2].setBackground(new Color(200, 0, 0));
                    labels[2].setForeground(Color.WHITE);
                } else if (status.contains("Rescue")) {
                    labels[2].setBackground(new Color(255, 165, 0));
                    labels[2].setForeground(Color.WHITE);
                } else if (status.contains("Overloaded")) {
                    labels[2].setBackground(new Color(255, 100, 100));
                    labels[2].setForeground(Color.WHITE);
                }
            }
        });
    }

    public JButton[] getAgentButtons(String agentName) {
        return agentButtons.get(agentName);
    }

    public void addMessage(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}