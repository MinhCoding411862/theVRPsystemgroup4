package testCase_8;
import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class DeliveryGUI extends JFrame {
    private JPanel masterPanel;
    private JPanel agentsPanel;
    private JTextArea logArea;
    private Map<String, AgentDisplay> agentDisplays = new HashMap<>();
    private JPanel masterBoxesPanel;
    private Map<String, JPanel> packagePanels = new HashMap<>();
    private Runnable onPauseCallback;
    private Runnable onResumeCallback;
    private boolean isPaused = false;
    private boolean autoScroll = true;

    // Statistics
    private int totalDeliveries = 0;
    private int totalConflicts = 0;
    private JLabel statsLabel;
    private JLabel optimizationLabel; // NEW: Shows items vs distance

    public DeliveryGUI() {
        setTitle("Negotiation-Based Delivery System - Item Priority");
        setSize(1100, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // === Master Panel ===
        masterPanel = new JPanel(new BorderLayout(10, 10));
        masterPanel.setBorder(BorderFactory.createTitledBorder("📦 Master - Available Packages"));
        masterPanel.setPreferredSize(new Dimension(240, 0));

        masterBoxesPanel = new JPanel();
        masterBoxesPanel.setLayout(new BoxLayout(masterBoxesPanel, BoxLayout.Y_AXIS));
        JScrollPane masterScroll = new JScrollPane(masterBoxesPanel);
        masterPanel.add(masterScroll, BorderLayout.CENTER);

        // Control panel
        JPanel controlPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JButton pauseButton = new JButton("⏸️ PAUSE");
        pauseButton.setFont(new Font("Arial", Font.BOLD, 12));
        pauseButton.setBackground(new Color(255, 165, 0));
        pauseButton.setForeground(Color.WHITE);
        pauseButton.setFocusPainted(false);
        pauseButton.addActionListener(e -> {
            if (isPaused) {
                resumeSystem();
                pauseButton.setText("⏸️ PAUSE");
                pauseButton.setBackground(new Color(255, 165, 0));
            } else {
                pauseSystem();
                pauseButton.setText("▶️ RESUME");
                pauseButton.setBackground(new Color(50, 150, 50));
            }
        });

        JButton clearLogButton = new JButton("🗑️ CLEAR LOG");
        clearLogButton.setFont(new Font("Arial", Font.BOLD, 12));
        clearLogButton.setBackground(new Color(100, 100, 200));
        clearLogButton.setForeground(Color.WHITE);
        clearLogButton.setFocusPainted(false);
        clearLogButton.addActionListener(e -> clearLog());

        JCheckBox autoScrollCheck = new JCheckBox("Auto-scroll", true);
        autoScrollCheck.setFont(new Font("Arial", Font.BOLD, 11));
        autoScrollCheck.addActionListener(e -> autoScroll = autoScrollCheck.isSelected());

        controlPanel.add(pauseButton);
        controlPanel.add(clearLogButton);
        controlPanel.add(autoScrollCheck);

        masterPanel.add(controlPanel, BorderLayout.SOUTH);
        add(masterPanel, BorderLayout.WEST);

        // === Agents Panel ===
        agentsPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        agentsPanel.setBorder(BorderFactory.createTitledBorder("🚚 Delivery Agents - Live Status"));
        add(agentsPanel, BorderLayout.CENTER);

        // === Log Area ===
        JPanel logPanel = new JPanel(new BorderLayout());

        // NEW: Optimization stats panel
        JPanel statsPanel = new JPanel(new GridLayout(2, 1, 0, 2));

        statsLabel = new JLabel("📊 Deliveries: 0 | Conflicts: 0 | Status: Running");
        statsLabel.setFont(new Font("Arial", Font.BOLD, 12));
        statsLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        statsLabel.setOpaque(true);
        statsLabel.setBackground(new Color(230, 240, 255));

        optimizationLabel = new JLabel("🎯 PRIORITY: Items: 0 | Distance: 0s");
        optimizationLabel.setFont(new Font("Arial", Font.BOLD, 12));
        optimizationLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        optimizationLabel.setOpaque(true);
        optimizationLabel.setBackground(new Color(255, 250, 230));

        statsPanel.add(statsLabel);
        statsPanel.add(optimizationLabel);
        logPanel.add(statsPanel, BorderLayout.NORTH);

        logArea = new JTextArea(10, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("📋 System Messages"));
        logPanel.add(logScroll, BorderLayout.CENTER);

        add(logPanel, BorderLayout.SOUTH);

        setVisible(true);
    }

    public void addMasterPackage(String packageName, int travelTime, int weight) {
        SwingUtilities.invokeLater(() -> {
            JPanel pkgPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            pkgPanel.setMaximumSize(new Dimension(220, 45));
            pkgPanel.setBorder(BorderFactory.createLineBorder(new Color(100, 150, 255), 2));
            pkgPanel.setBackground(new Color(240, 245, 255));

            JLabel boxIcon = new JLabel("📦");
            boxIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));

            JLabel pkgLabel = new JLabel("<html>" + packageName + "<br>" +
                    travelTime + "s, " + weight + "kg</html>");
            pkgLabel.setFont(new Font("Arial", Font.BOLD, 11));

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

    public void addAgent(String agentName, int capacity) {
        JPanel agentPanel = new JPanel();
        agentPanel.setLayout(new BoxLayout(agentPanel, BoxLayout.Y_AXIS));
        agentPanel.setBorder(BorderFactory.createTitledBorder("🚚 " + agentName));

        JLabel statusLabel = new JLabel("Status: Idle", SwingConstants.CENTER);
        JLabel loadLabel = new JLabel("Load: 0/" + capacity + "kg", SwingConstants.CENTER);
        JLabel packagesLabel = new JLabel("Packages: None", SwingConstants.CENTER);
        JLabel travelTimeLabel = new JLabel("Travel Time: 0s", SwingConstants.CENTER);
        JLabel speedLabel = new JLabel("Speed: Normal", SwingConstants.CENTER);

        styleLabel(statusLabel, new Color(128, 128, 128));
        styleLabel(loadLabel, new Color(200, 200, 200));
        styleLabel(packagesLabel, new Color(240, 248, 255));
        styleLabel(travelTimeLabel, new Color(255, 250, 240));
        styleLabel(speedLabel, new Color(255, 255, 200));

        packagesLabel.setFont(new Font("Arial", Font.BOLD, 11));
        loadLabel.setFont(new Font("Arial", Font.BOLD, 12));
        speedLabel.setFont(new Font("Arial", Font.BOLD, 11));

        agentPanel.add(statusLabel);
        agentPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        agentPanel.add(loadLabel);
        agentPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        agentPanel.add(packagesLabel);
        agentPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        agentPanel.add(travelTimeLabel);
        agentPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        agentPanel.add(speedLabel);

        agentsPanel.add(agentPanel);
        agentsPanel.revalidate();
        agentsPanel.repaint();

        AgentDisplay display = new AgentDisplay(statusLabel, loadLabel, packagesLabel, travelTimeLabel, speedLabel, capacity);
        agentDisplays.put(agentName, display);
    }

    private void styleLabel(JLabel label, Color bgColor) {
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                BorderFactory.createEmptyBorder(8, 5, 8, 5)
        ));
        label.setOpaque(true);
        label.setBackground(bgColor);
    }

    public void updateAgentStatus(String agentName, String status) {
        SwingUtilities.invokeLater(() -> {
            AgentDisplay display = agentDisplays.get(agentName);
            if (display != null) {
                display.statusLabel.setText("Status: " + status);

                switch (status) {
                    case "AT_MASTER" -> {
                        display.statusLabel.setBackground(new Color(128, 128, 128));
                        display.statusLabel.setForeground(Color.WHITE);
                    }
                    case "DELIVERING" -> {
                        display.statusLabel.setBackground(new Color(30, 144, 255));
                        display.statusLabel.setForeground(Color.WHITE);
                    }
                    case "RETURNING" -> {
                        display.statusLabel.setBackground(new Color(255, 140, 0));
                        display.statusLabel.setForeground(Color.WHITE);
                    }
                    default -> {
                        display.statusLabel.setBackground(new Color(200, 200, 200));
                        display.statusLabel.setForeground(Color.BLACK);
                    }
                }
            }
        });
    }

    public void updateAgentLoad(String agentName, int currentLoad, int capacity) {
        SwingUtilities.invokeLater(() -> {
            AgentDisplay display = agentDisplays.get(agentName);
            if (display != null) {
                display.loadLabel.setText("Load: " + currentLoad + "/" + capacity + "kg");

                float percentage = (float) currentLoad / capacity;
                if (percentage >= 1.0f) {
                    display.loadLabel.setBackground(new Color(255, 100, 100));
                } else if (percentage >= 0.7f) {
                    display.loadLabel.setBackground(new Color(255, 200, 100));
                } else if (percentage > 0) {
                    display.loadLabel.setBackground(new Color(144, 238, 144));
                } else {
                    display.loadLabel.setBackground(new Color(200, 200, 200));
                }
            }
        });
    }

    public void updateAgentPackages(String agentName, String packages) {
        SwingUtilities.invokeLater(() -> {
            AgentDisplay display = agentDisplays.get(agentName);
            if (display != null) {
                display.packagesLabel.setText("Packages: " + packages);

                if ("None".equals(packages) || packages.contains("[0/")) {
                    display.packagesLabel.setForeground(Color.GRAY);
                } else {
                    display.packagesLabel.setForeground(new Color(0, 100, 0));
                }
            }
        });
    }

    public void updateAgentTravelTime(String agentName, int timeLeft) {
        SwingUtilities.invokeLater(() -> {
            AgentDisplay display = agentDisplays.get(agentName);
            if (display != null) {
                display.travelTimeLabel.setText("Travel Time: " + timeLeft + "s");

                if (timeLeft > 0) {
                    display.travelTimeLabel.setForeground(new Color(255, 100, 0));
                    display.travelTimeLabel.setFont(new Font("Arial", Font.BOLD, 12));
                } else {
                    display.travelTimeLabel.setForeground(Color.GRAY);
                    display.travelTimeLabel.setFont(new Font("Arial", Font.PLAIN, 11));
                }
            }
        });
    }

    public void updateAgentSpeed(String agentName, String speedType, double factor) {
        SwingUtilities.invokeLater(() -> {
            AgentDisplay display = agentDisplays.get(agentName);
            if (display != null && display.speedLabel != null) {
                display.speedLabel.setText("Speed: " + speedType + " (x" + String.format("%.1f", factor) + ")");

                Color bg = switch (speedType) {
                    case "Fast" -> new Color(0, 180, 0);
                    case "Slow" -> new Color(200, 0, 0);
                    default -> new Color(0, 100, 200);
                };
                display.speedLabel.setBackground(bg);
                display.speedLabel.setForeground(Color.WHITE);
            }
        });
    }

    public void updateAgentCapacity(String agentName, int capacity) {
        SwingUtilities.invokeLater(() -> {
            AgentDisplay display = agentDisplays.get(agentName);
            if (display != null) {
                display.capacity = capacity;
                display.loadLabel.setText("Load: 0/" + capacity + "kg");
            }
        });
    }

    // NEW: Update optimization statistics
    public void updateOptimizationStats(int totalItems, int totalDistance) {
        SwingUtilities.invokeLater(() -> {
            double efficiency = totalDistance > 0 ? (double) totalItems / totalDistance : 0;
            optimizationLabel.setText(String.format(
                    "🎯 PRIORITY: Items: %d | Distance: %ds | Efficiency: %.3f items/s",
                    totalItems, totalDistance, efficiency
            ));

            // Color based on efficiency
            if (efficiency > 0.1) {
                optimizationLabel.setBackground(new Color(200, 255, 200)); // Green - excellent
            } else if (efficiency > 0.05) {
                optimizationLabel.setBackground(new Color(255, 250, 200)); // Yellow - good
            } else {
                optimizationLabel.setBackground(new Color(255, 230, 230)); // Red - poor
            }
        });
    }

    public void addMessage(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");

            if (msg.contains("CAPACITY CONFLICT") || msg.contains("CAPACITY FULL")) {
                totalConflicts++;
                updateStats();
            }

            if (autoScroll) {
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
        });
    }

    private void updateStats() {
        String status = isPaused ? "PAUSED" : "Running";
        statsLabel.setText("📊 Deliveries: " + totalDeliveries +
                " | Conflicts: " + totalConflicts +
                " | Status: " + status);

        if (isPaused) {
            statsLabel.setBackground(new Color(255, 230, 200));
        } else {
            statsLabel.setBackground(new Color(230, 240, 255));
        }
    }

    private void clearLog() {
        logArea.setText("");
        addMessage("🗑️ Log cleared");
        addMessage("📊 Statistics preserved");
        addMessage("");
    }

    private void pauseSystem() {
        isPaused = true;
        addMessage("\n⏸️ SYSTEM PAUSED\n");
        updateStats();

        if (onPauseCallback != null) {
            onPauseCallback.run();
        }
    }

    private void resumeSystem() {
        isPaused = false;
        addMessage("\n▶️ SYSTEM RESUMED\n");
        updateStats();

        if (onResumeCallback != null) {
            onResumeCallback.run();
        }
    }

    public void setOnPauseCallback(Runnable callback) {
        this.onPauseCallback = callback;
    }

    public void setOnResumeCallback(Runnable callback) {
        this.onResumeCallback = callback;
    }

    public void incrementDeliveryCount() {
        totalDeliveries++;
        updateStats();
    }

    public void incrementConflictCount() {
        totalConflicts++;
        updateStats();
    }

    static class AgentDisplay {
        JLabel statusLabel;
        JLabel loadLabel;
        JLabel packagesLabel;
        JLabel travelTimeLabel;
        JLabel speedLabel;
        int capacity;

        AgentDisplay(JLabel statusLabel, JLabel loadLabel, JLabel packagesLabel,
                     JLabel travelTimeLabel, JLabel speedLabel, int capacity) {
            this.statusLabel = statusLabel;
            this.loadLabel = loadLabel;
            this.packagesLabel = packagesLabel;
            this.travelTimeLabel = travelTimeLabel;
            this.speedLabel = speedLabel;
            this.capacity = capacity;
        }
    }
}