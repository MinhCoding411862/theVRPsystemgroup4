package testCase_7;

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
        gui.addMessage("=========================================");
        gui.addMessage("🎯 MASTER AGENT INITIALIZED");
        gui.addMessage("=========================================");
        gui.addMessage("📋 System Mode: SHARED PACKAGES");
        gui.addMessage("   → Any agent can pick up any package");
        gui.addMessage("   → First-come, first-served basis");
        gui.addMessage("=========================================");
        gui.addMessage("");

        // Set up pause/resume callbacks
        gui.setOnPauseCallback(() -> pauseSystem());
        gui.setOnResumeCallback(() -> resumeSystem());

        // Create initial packages
        createPackage("P1", 3, 8);
        createPackage("P2", 5, 7);
        createPackage("P3", 2, 5);
        createPackage("P4", 4, 6);
        createPackage("P5", 3, 4);
        createPackage("P6", 6, 9);
        createPackage("P7", 2, 3);
        createPackage("P8", 4, 7);
        createPackage("P9", 5, 6);
        createPackage("P10", 3, 5);

        gui.addMessage("✅ All packages created with uniform travel times");
        gui.addMessage("");

        // Create 3 Delivery Agents with 15kg capacity
        for (int i = 1; i <= agentCount; i++) {
            String agentName = "Agent" + i;
            gui.addAgent(agentName, 15);
        }

        // Listen for agent READY signals
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

                // Listen for package requests
                MessageTemplate requestMt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
                ACLMessage requestMsg = receive(requestMt);

                if (requestMsg != null) {
                    handlePackageRequest(requestMsg);
                    return;
                }

                // Listen for package pickups (confirmation)
                MessageTemplate pickupMt = MessageTemplate.MatchPerformative(ACLMessage.CONFIRM);
                ACLMessage pickupMsg = receive(pickupMt);
                if (pickupMsg != null) {
                    handlePackagePickup(pickupMsg);
                    return;
                }

                // Listen for delivery completions
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
        if (systemPaused) return;

        String agentName = msg.getSender().getLocalName();
        String content = msg.getContent();

        String[] parts = content.split(":");
        int currentLoad = Integer.parseInt(parts[1]);
        int capacity = Integer.parseInt(parts[2]);
        ACLMessage reply = msg.createReply();

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
                availablePackages.remove(selectedPackage);

                reply.setPerformative(ACLMessage.PROPOSE);
                reply.setContent(selectedPackage + ":" + selectedInfo.travelTime + ":" + selectedInfo.weight);
                send(reply);

                gui.addMessage("📤 " + agentName + " offered " + selectedPackage +
                        " (" + selectedInfo.travelTime + "s, " + selectedInfo.weight + "kg)");

                gui.removeMasterPackage(selectedPackage);
            } else {
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

        gui.addMessage("✅ " + agentName + " confirmed pickup of " + packageName);

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

        if (systemPaused) return;

        synchronized (availablePackages) {
            for (int i = 0; i < 2; i++) {
                int pkgNum = (int) (Math.random() * 1000);
                String newPkgName = "P" + pkgNum;
                int travelTime = 2 + (int) (Math.random() * 5);
                int weight = 3 + (int) (Math.random() * 7);

                createPackage(newPkgName, travelTime, weight);
            }
            gui.addMessage("📦 Created 2 new packages (total available: " + availablePackages.size() + ")");
        }
    }

    private void pauseSystem() {
        systemPaused = true;

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
        synchronized (availablePackages) {
            if (availablePackages.size() < 5) {
                for (int i = 0; i < 5; i++) {
                    int pkgNum = (int) (Math.random() * 1000);
                    String newPkgName = "P" + pkgNum;
                    int travelTime = 2 + (int) (Math.random() * 5);
                    int weight = 3 + (int) (Math.random() * 7);

                    createPackage(newPkgName, travelTime, weight);
                }
                gui.addMessage("📦 Created 5 new packages for resumed operations");
            }
        }

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