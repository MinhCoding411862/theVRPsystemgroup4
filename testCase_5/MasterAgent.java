package testCase_6;

import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.*;

public class MasterAgent extends Agent {
    public static DeliveryGUI gui;
    private Map<String, PackageInfo> availablePackages = new LinkedHashMap<>();
    private int agentCount = 3;
    private Set<String> readyAgents = new HashSet<>();
    private boolean systemPaused = false;

    @Override
    protected void setup() {
        gui = new DeliveryGUI();
        gui.addMessage("========================================");
        gui.addMessage("🎯 MASTER AGENT INITIALIZED");
        gui.addMessage("========================================");
        gui.addMessage("📋 System Mode: SHARED PACKAGES");
        gui.addMessage("   → Any agent can pick up any package");
        gui.addMessage("   → First-come, first-served basis");
        gui.addMessage("========================================");
        gui.addMessage("");

        // Set up pause/resume callbacks
        gui.setOnPauseCallback(() -> pauseSystem());
        gui.setOnResumeCallback(() -> resumeSystem());

        // === Create initial packages with UNIFORM travel times and weights ===
        createPackage("P1", 3, 8);   // 3s travel time, 8kg
        createPackage("P2", 5, 7);   // 5s travel time, 7kg
        createPackage("P3", 2, 5);   // 2s travel time, 5kg
        createPackage("P4", 4, 6);   // 4s travel time, 6kg
        createPackage("P5", 3, 4);   // 3s travel time, 4kg
        createPackage("P6", 6, 9);   // 6s travel time, 9kg
        createPackage("P7", 2, 3);   // 2s travel time, 3kg
        createPackage("P8", 4, 7);   // 4s travel time, 7kg
        createPackage("P9", 5, 6);   // 5s travel time, 6kg
        createPackage("P10", 3, 5);  // 3s travel time, 5kg

        gui.addMessage("✅ All packages created with uniform travel times");
        gui.addMessage("");

        // === Create 3 Delivery Agents with 15kg capacity ===
        for (int i = 1; i <= agentCount; i++) {
            String agentName = "Agent" + i;
            gui.addAgent(agentName, 15); // capacity = 15kg
        }

        // === Listen for agent READY signals ===
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate readyMt = MessageTemplate.MatchPerformative(ACLMessage.SUBSCRIBE);
                ACLMessage readyMsg = receive(readyMt);

                if (readyMsg != null) {
                    String agentName = readyMsg.getSender().getLocalName();
                    readyAgents.add(agentName);
                    gui.addMessage("✅ " + agentName + " is READY");

                    // When all agents ready, start the system
                    if (readyAgents.size() == agentCount) {
                        gui.addMessage("");
                        gui.addMessage("🚀 All agents ready! System started.");
                        gui.addMessage("");

                        // Send START signal to all agents
                        for (String agent : readyAgents) {
                            ACLMessage startMsg = new ACLMessage(ACLMessage.INFORM);
                            startMsg.setContent("START");
                            startMsg.addReceiver(new AID(agent, AID.ISLOCALNAME));
                            send(startMsg);
                        }
                    }
                    return;
                }

                // === Listen for package requests ===
                MessageTemplate requestMt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
                ACLMessage requestMsg = receive(requestMt);

                if (requestMsg != null) {
                    handlePackageRequest(requestMsg);
                    return;
                }

                // === Listen for package pickups (confirmation) ===
                MessageTemplate pickupMt = MessageTemplate.MatchPerformative(ACLMessage.CONFIRM);
                ACLMessage pickupMsg = receive(pickupMt);
                if (pickupMsg != null) {
                    handlePackagePickup(pickupMsg);
                    return;
                }

                // === Listen for delivery completions ===
                MessageTemplate deliveredMt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
                ACLMessage deliveredMsg = receive(deliveredMt);

                if (deliveredMsg != null && deliveredMsg.getContent().startsWith("DELIVERED:")) {
                    handleDeliveryComplete(deliveredMsg);
                    return;
                }

                block();
            }
        });
    }

    private void createPackage(String name, int travelTime, int weight) {
        PackageInfo pkg = new PackageInfo(name, travelTime, weight);
        availablePackages.put(name, pkg);
        gui.addMasterPackage(name, travelTime, weight);
    }

    private void handlePackageRequest(ACLMessage msg) {
        // Don't process requests when paused
        if (systemPaused) {
            return;
        }

        String agentName = msg.getSender().getLocalName();
        String content = msg.getContent();

        // Format: "REQUEST:currentLoad:capacity"
        String[] parts = content.split(":");
        int currentLoad = Integer.parseInt(parts[1]);
        int capacity = Integer.parseInt(parts[2]);
        ACLMessage reply = msg.createReply();

        // Find first available package that fits capacity
        String selectedPackage = null;
        PackageInfo selectedInfo = null;

        synchronized (availablePackages) {
            for (Map.Entry<String, PackageInfo> entry : availablePackages.entrySet()) {
                PackageInfo pkg = entry.getValue();
                if (currentLoad + pkg.weight <= capacity) {
                    selectedPackage = entry.getKey();
                    selectedInfo = pkg;
                    break;
                }
            }
            if (selectedPackage != null) {
                // IMMEDIATELY remove package from available list to prevent double-assignment
                availablePackages.remove(selectedPackage);

                // Package found - send it to agent
                reply.setPerformative(ACLMessage.PROPOSE);
                reply.setContent(selectedPackage + ":" + selectedInfo.travelTime + ":" + selectedInfo.weight);
                send(reply);

                gui.addMessage("📤 " + agentName + " offered " + selectedPackage +
                        " (" + selectedInfo.travelTime + "s, " + selectedInfo.weight + "kg)");

                // Remove from GUI
                gui.removeMasterPackage(selectedPackage);
            } else {
                // No suitable package available
                reply.setPerformative(ACLMessage.REFUSE);

                if (availablePackages.isEmpty()) {
                    reply.setContent("NO_PACKAGES");
                    gui.addMessage("⚠️ " + agentName + " requested package but none available");
                } else {
                    reply.setContent("OVERWEIGHT");
                    gui.addMessage("⚠️ " + agentName + " CAPACITY CONFLICT - all packages too heavy [" +
                            currentLoad + "/" + capacity + "kg]");
                    gui.incrementConflictCount();
                }
                send(reply);
            }
        }
    }

    private void handlePackagePickup(ACLMessage msg) {
        String packageName = msg.getContent();
        String agentName = msg.getSender().getLocalName();

        // Package was already removed when offered, just log the confirmation
        gui.addMessage("✅ " + agentName + " confirmed pickup of " + packageName);

        // Show remaining packages
        synchronized (availablePackages) {
            if (availablePackages.isEmpty()) {
                gui.addMessage("📦No packages remaining");
            } else {
                gui.addMessage("📦Remaining: " + availablePackages.keySet());
            }
        }
    }

    private void handleDeliveryComplete(ACLMessage msg) {
        String content = msg.getContent();
        String packageName = content.substring("DELIVERED:".length());
        String agentName = msg.getSender().getLocalName();

        gui.addMessage("🎉 " + agentName + " completed delivery of " + packageName);
        gui.incrementDeliveryCount();

        // Don't create new packages when paused
        if (systemPaused) {
            return;
        }

        // Create new packages to keep the system running (create 2 packages per delivery)
        synchronized (availablePackages) {
            for (int i = 0; i < 2; i++) {
                int pkgNum = (int) (Math.random() * 1000);
                String newPkgName = "P" + pkgNum;
                int travelTime = 2 + (int) (Math.random() * 5); // 2-6s
                int weight = 3 + (int) (Math.random() * 7); // 3-9kg

                createPackage(newPkgName, travelTime, weight);
            }
            gui.addMessage("📦 Created 2 new packages (total available: " + availablePackages.size() + ")");
        }
    }

    private void pauseSystem() {
        systemPaused = true;

        // Send PAUSE message to all agents
        for (String agentName : readyAgents) {
            ACLMessage pauseMsg = new ACLMessage(ACLMessage.INFORM);
            pauseMsg.setContent("PAUSE");
            pauseMsg.addReceiver(new AID(agentName, AID.ISLOCALNAME));
            send(pauseMsg);
        }
    }

    private void resumeSystem() {
        systemPaused = false;
        gui.addMessage("▶️ System creating new packages after resume...");
        // Create some new packages when resuming if needed
        synchronized (availablePackages) {
            if (availablePackages.size() < 5) {
                for (int i = 0; i < 5; i++) {
                    int pkgNum = (int) (Math.random() * 1000);
                    String newPkgName = "P" + pkgNum;
                    int travelTime = 2 + (int) (Math.random() * 5); // 2-6s
                    int weight = 3 + (int) (Math.random() * 7); // 3-9kg

                    createPackage(newPkgName, travelTime, weight);
                }
                gui.addMessage("📦 Created 5 new packages for resumed operations");
            }
        }

        // Send RESUME message to all agents
        for (String agentName : readyAgents) {
            ACLMessage resumeMsg = new ACLMessage(ACLMessage.INFORM);
            resumeMsg.setContent("RESUME");
            resumeMsg.addReceiver(new AID(agentName, AID.ISLOCALNAME));
            send(resumeMsg);
        }
    }
    static class PackageInfo {
        String name;
        int travelTime;
        int weight;

        PackageInfo(String name, int travelTime, int weight) {
            this.name = name;
            this.travelTime = travelTime;
            this.weight = weight;
        }
    }
}