package testCase_6;
import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DeliveryAgent extends Agent {
    private int capacity = 15; // Max carrying capacity in 15 kg
    private int currentLoad = 0; // Current load in kg
    private List<PackageInTransit> packagesCarrying = new ArrayList<>();
    private boolean isAtMaster = true;
    private boolean started = false;
    private boolean paused = false;

    @Override
    protected void setup() {
        // Get capacity from arguments (optional)
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            capacity = Integer.parseInt(args[0].toString());
        }
        // Default is now 15kg (set in class field declaration)

        System.out.println(getLocalName() + " created with capacity " + capacity + "kg");

        // Send READY signal to master
        ACLMessage readyMsg = new ACLMessage(ACLMessage.SUBSCRIBE);
        readyMsg.setContent("READY");
        readyMsg.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
        send(readyMsg);

        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();

                if (msg != null) {
                    // Handle PAUSE signal
                    if (msg.getPerformative() == ACLMessage.INFORM && "PAUSE".equals(msg.getContent())) {
                        paused = true;
                        return;
                    }

                    // Handle RESUME signal
                    if (msg.getPerformative() == ACLMessage.INFORM && "RESUME".equals(msg.getContent())) {
                        paused = false;
                        SwingUtilities.invokeLater(() -> {
                            MasterAgent.gui.addMessage("▶️ " + getLocalName() + " resuming operations");
                        });

                        // If agent is at master, restart requesting immediately
                        if (isAtMaster) {
                            doWait(1000); // Small delay to let master create packages
                            requestPackage();
                        }
                        return;
                    }

                    // Wait for START signal
                    if (!started && msg.getPerformative() == ACLMessage.INFORM &&
                            "START".equals(msg.getContent())) {
                        started = true;
                        requestPackage();
                        return;
                    }

                    // Handle package offer from master
                    if (msg.getPerformative() == ACLMessage.PROPOSE) {
                        handlePackageOffer(msg);
                        return;
                    }

                    // Handle rejection (no packages or overweight)
                    if (msg.getPerformative() == ACLMessage.REFUSE) {
                        handleRefusal(msg);
                        return;
                    }
                } else {
                    block();
                }
            }
        });
    }

    private void requestPackage() {
        if (!isAtMaster || paused) return;
        ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
        request.setContent("REQUEST:" + currentLoad + ":" + capacity);
        request.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
        send(request);
    }

    private void handlePackageOffer(ACLMessage msg) {
        String[] parts = msg.getContent().split(":");
        String packageName = parts[0];
        int travelTime = Integer.parseInt(parts[1]);
        int weight = Integer.parseInt(parts[2]);

        // Check if we can carry it
        if (currentLoad + weight > capacity) {
            SwingUtilities.invokeLater(() -> {
                MasterAgent.gui.addMessage("❌ " + getLocalName() + " CANNOT carry " +
                        packageName + " - would exceed capacity!");
            });
            requestPackage(); // Try to get another package
            return;
        }

        // Accept the package
        currentLoad += weight;
        PackageInTransit pkg = new PackageInTransit(packageName, travelTime, weight);
        packagesCarrying.add(pkg);

        // Confirm pickup to master
        ACLMessage confirm = new ACLMessage(ACLMessage.CONFIRM);
        confirm.setContent(packageName);
        confirm.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
        send(confirm);

        // Update GUI
        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.updateAgentLoad(getLocalName(), currentLoad, capacity);
            MasterAgent.gui.updateAgentPackages(getLocalName(), getPackageNames());
        });

        // Check if we can carry more
        if (currentLoad < capacity) {
            // Try to get another package
            SwingUtilities.invokeLater(() -> {
                MasterAgent.gui.addMessage("🔄 REQUEST → " + getLocalName() + " | Has space, requesting more [" + currentLoad + "/" + capacity + "kg]");
            });
            doWait(500); // Small delay
            if (!paused) {
                requestPackage();
            }
        } else {
            // Capacity full, start delivery
            SwingUtilities.invokeLater(() -> {
                MasterAgent.gui.addMessage("🚚 DEPARTURE → " + getLocalName() + " | Full capacity [" + currentLoad + "/" + capacity + "kg] → Starting delivery");
            });
            startDelivery();
        }
    }

    private void handleRefusal(ACLMessage msg) {
        String reason = msg.getContent();

        if ("NO_PACKAGES".equals(reason)) {
            SwingUtilities.invokeLater(() -> {
                MasterAgent.gui.addMessage("⏳ " + getLocalName() + " waiting - no packages available");
            });
            doWait(2000);
            if (!paused) {
                requestPackage();
            }
        } else if ("OVERWEIGHT".equals(reason)) {
            if (packagesCarrying.isEmpty()) {
                // Can't carry anything - wait for lighter packages
                SwingUtilities.invokeLater(() -> {
                    MasterAgent.gui.addMessage("⏳ " + getLocalName() + " waiting - all packages too heavy");
                });
                doWait(2000);
                if (!paused) {
                    requestPackage();
                }
            } else {
                // We have some packages, start delivery
                SwingUtilities.invokeLater(() -> {
                    MasterAgent.gui.addMessage("🚚 " + getLocalName() + " starting delivery with current load");
                });
                startDelivery();
            }
        }
    }

    private void startDelivery() {
        if (packagesCarrying.isEmpty()) return;

        isAtMaster = false;

        // Sort packages by travel time (shortest first for efficiency)
        packagesCarrying.sort(Comparator.comparingInt(p -> p.travelTime));

        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.updateAgentStatus(getLocalName(), "DELIVERING");
        });

        deliverNextPackage();
    }

    private void deliverNextPackage() {
        if (packagesCarrying.isEmpty()) {
            returnToMaster();
            return;
        }

        PackageInTransit pkg = packagesCarrying.get(0);

        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.addMessage("🚚 " + getLocalName() + " delivering " + pkg.name +
                    " (travel: " + pkg.travelTime + "s)");
            MasterAgent.gui.updateAgentTravelTime(getLocalName(), pkg.travelTime);
        });

        // Simulate delivery with timer
        Timer deliveryTimer = new Timer(1000, null);
        final int[] timeLeft = {pkg.travelTime};

        deliveryTimer.addActionListener(e -> {
            timeLeft[0]--;
            SwingUtilities.invokeLater(() -> {
                MasterAgent.gui.updateAgentTravelTime(getLocalName(), timeLeft[0]);
            });

            if (timeLeft[0] == 0) {
                deliveryTimer.stop();
                completeDelivery(pkg);
            }
        });
        deliveryTimer.start();
    }

    private void completeDelivery(PackageInTransit pkg) {
        currentLoad -= pkg.weight;
        packagesCarrying.remove(pkg);

        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.updateAgentLoad(getLocalName(), currentLoad, capacity);
            MasterAgent.gui.updateAgentPackages(getLocalName(), getPackageNames());
        });

        // Notify master
        ACLMessage deliveredMsg = new ACLMessage(ACLMessage.INFORM);
        deliveredMsg.setContent("DELIVERED:" + pkg.name);
        deliveredMsg.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
        send(deliveredMsg);

        // Continue with next package
        doWait(500);
        deliverNextPackage();
    }

    private void returnToMaster() {
        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.addMessage("🔙 " + getLocalName() + " returning to Master");
            MasterAgent.gui.updateAgentStatus(getLocalName(), "RETURNING");
        });

        // Simulate return journey (use average time)
        int returnTime = 3;
        Timer returnTimer = new Timer(1000, null);
        final int[] timeLeft = {returnTime};

        returnTimer.addActionListener(e -> {
            timeLeft[0]--;
            SwingUtilities.invokeLater(() -> {
                MasterAgent.gui.updateAgentTravelTime(getLocalName(), timeLeft[0]);
            });

            if (timeLeft[0] == 0) {
                returnTimer.stop();
                arriveAtMaster();
            }
        });
        returnTimer.start();
    }

    private void arriveAtMaster() {
        isAtMaster = true;

        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.addMessage("✅ " + getLocalName() + " arrived at Master");
            MasterAgent.gui.updateAgentStatus(getLocalName(), "AT_MASTER");
            MasterAgent.gui.updateAgentTravelTime(getLocalName(), 0);
        });

        doWait(1000);
        if (!paused) {
            requestPackage();
        }
    }

    private String getPackageNames() {
        if (packagesCarrying.isEmpty()) return "None";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < packagesCarrying.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(packagesCarrying.get(i).name);
        }
        return sb.toString();
    }

    @Override
    protected void takeDown() {
        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.addMessage("🛑 " + getLocalName() + " terminated.");
        });
    }

    static class PackageInTransit {
        String name;
        int travelTime;
        int weight;

        PackageInTransit(String name, int travelTime, int weight) {
            this.name = name;
            this.travelTime = travelTime;
            this.weight = weight;
        }
    }
}