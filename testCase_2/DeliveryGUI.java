package testCase_2;
import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class DeliveryGUI extends JFrame {
    private JPanel masterPanel;
    private JPanel agentsPanel;
    private JTextArea logArea;
    private Map<String, JLabel[]> agentLabels = new HashMap<>();
    private JPanel masterBoxesPanel;

    public DeliveryGUI() {
        setTitle("Delivery System Monitor");
        setSize(800, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // === Master Panel ===
        masterPanel = new JPanel(new BorderLayout(10, 10));
        masterPanel.setBorder(BorderFactory.createTitledBorder("Master Agent"));

        masterBoxesPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        JScrollPane masterScroll = new JScrollPane(masterBoxesPanel);
        masterPanel.add(masterScroll, BorderLayout.CENTER);

        add(masterPanel, BorderLayout.WEST);

        // === Agents Panel ===
        agentsPanel = new JPanel(new GridLayout(0, 3, 10, 10));
        agentsPanel.setBorder(BorderFactory.createTitledBorder("Delivery Agents"));
        add(agentsPanel, BorderLayout.CENTER);

        // === Log Area ===
        logArea = new JTextArea(6, 50);
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Messages"));
        add(logScroll, BorderLayout.SOUTH);

        setVisible(true);
    }

    public void addMasterPackage(String packageName, int deliveryTime) {
        JPanel pkgPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel boxIcon = new JLabel("\uD83D\uDCE6"); // 📦 icon
        JLabel pkgLabel = new JLabel(" " + packageName + " , " + deliveryTime + "s");
        pkgPanel.add(boxIcon);
        pkgPanel.add(pkgLabel);
        masterBoxesPanel.add(pkgPanel);
        masterBoxesPanel.revalidate();
        masterBoxesPanel.repaint();
    }

    public void addAgent(String agentName) {
        JPanel agentPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        agentPanel.setBorder(BorderFactory.createTitledBorder(agentName));

        JLabel timeToMasterLabel = new JLabel("Time to Master: 0", SwingConstants.CENTER);
        JLabel deliveryTimeLabel = new JLabel("Delivery Time: 0", SwingConstants.CENTER);
        JLabel statusLabel = new JLabel("Status: Idle", SwingConstants.CENTER);

        // Wrap in borders for clarity
        timeToMasterLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        deliveryTimeLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        statusLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));

        agentPanel.add(timeToMasterLabel);
        agentPanel.add(deliveryTimeLabel);
        agentPanel.add(statusLabel);

        agentsPanel.add(agentPanel);
        agentsPanel.revalidate();
        agentsPanel.repaint();

        agentLabels.put(agentName, new JLabel[]{timeToMasterLabel, deliveryTimeLabel, statusLabel});
    }

    public void updateAgentTimes(String agentName, int timeToMaster, int deliveryTime) {
        JLabel[] labels = agentLabels.get(agentName);
        if (labels != null) {
            labels[0].setText("Time to Master: " + timeToMaster);
            labels[1].setText("Delivery Time: " + deliveryTime);

            if (timeToMaster > 0) {
                labels[2].setText("Status: Heading to Master");
                labels[2].setForeground(Color.ORANGE);
            } else if (deliveryTime > 0) {
                labels[2].setText("Status: Delivering");
                labels[2].setForeground(Color.BLUE);
            } else {
                labels[2].setText("Status: Idle");
                labels[2].setForeground(Color.GRAY);
            }
        }
    }

    public void addMessage(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}
