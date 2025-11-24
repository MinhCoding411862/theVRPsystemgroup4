package testCase_6;

import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;

import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedList;
import java.util.Queue;
import java.util.ArrayList;
import java.util.List;

public class MasterAgent extends Agent {
    public static DeliveryGUI gui;
    private Map<String, String> agentPackageMapping = new HashMap<>();
    private Map<String, PackageInfo> activePackages = new HashMap<>();
    private Map<String, String> agentStates = new HashMap<>();
    private Map<String, Integer> packageCounters = new HashMap<>();
    private Map<String, AgentController> agentControllers = new HashMap<>();
    private Map<String, Queue<String>> packageQueues = new HashMap<>();
    private Map<String, Boolean> agentOnRescueMission = new HashMap<>(); // Track rescue missions
    private Map<String, List<RescueBid>> rescueBids = new HashMap<>(); // ConversationId -> List of bids

    private class PackageInfo {
        String packageName;
        String packageType;
        int deliveryTime;
        boolean available;
        boolean inDelivery;
        String assignedAgent;

        PackageInfo(String packageName, String packageType, int deliveryTime) {
            this.packageName = packageName;
            this.packageType = packageType;
            this.deliveryTime = deliveryTime;
            this.available = true;
            this.inDelivery = false;
            this.assignedAgent = null;
        }
    }

    private class RescueBid {
        String agentName;
        String strandedAgent;
        String packageName;
        int bidTime;
        String conversationId;

        RescueBid(String agentName, String strandedAgent, String packageName, int bidTime, String conversationId) {
            this.agentName = agentName;
            this.strandedAgent = strandedAgent;
            this.packageName = packageName;
            this.bidTime = bidTime;
            this.conversationId = conversationId;
        }
    }

    @Override
    protected void setup() {
        gui = new DeliveryGUI();
        gui.addMessage("MasterAgent is ready.");

        // Initialize package counters and queues
        for (int i = 1; i <= 3; i++) {
            packageCounters.put(String.valueOf(i), 1);
            packageQueues.put(String.valueOf(i), new LinkedList<>());
            agentOnRescueMission.put("Agent" + i, false);
        }

        // Create 3 Delivery Agents
        for (int i = 1; i <= 3; i++) {
            String agentName = "Agent" + i;
            String packageType = String.valueOf(i);

            gui.addAgent(agentName);
            agentPackageMapping.put(agentName, packageType);
            agentStates.put(agentName, "IDLE");

            try {
                AgentController ac = getContainerController().createNewAgent(
                        agentName,
                        "testCase_6.DeliveryAgent",
                        new Object[]{packageType}
                );
                ac.start();
                agentControllers.put(agentName, ac);
            } catch (StaleProxyException e) {
                e.printStackTrace();
            }
        }

        // Connect GUI buttons after 3 seconds
        addBehaviour(new WakerBehaviour(this, 3000) {
            @Override
            protected void onWake() {
                connectGUIButtons();
            }
        });

        // Initialize first batch of packages
        for (int i = 1; i <= 3; i++) {
            String packageType = String.valueOf(i);
            createNewPackage(packageType);
        }

        // Listen for distress calls (CFP)
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
                ACLMessage msg = receive(mt);

                if (msg != null) {
                    String content = msg.getContent();
                    if (content != null && content.startsWith("DISTRESS_CALL:")) {
                        handleDistressCall(msg);
                    }
                } else {
                    block();
                }
            }
        });

        // Listen for rescue bids (PROPOSE)
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
                ACLMessage msg = receive(mt);

                if (msg != null) {
                    String content = msg.getContent();
                    if (content != null && content.startsWith("RESCUE_BID:")) {
                        handleRescueBid(msg);
                    }
                } else {
                    block();
                }
            }
        });

        // Listen for delivery completions and rescue completions
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
                ACLMessage msg = receive(mt);

                if (msg != null) {
                    String content = msg.getContent();
                    String agentName = msg.getSender().getLocalName();

                    if (content != null && content.startsWith("DELIVERY_COMPLETE:")) {
                        String[] parts = content.split(":");
                        if (parts.length >= 2) {
                            String completedPackage = parts[1];

                            PackageInfo pkgInfo = activePackages.get(completedPackage);
                            if (pkgInfo != null) {
                                String packageType = pkgInfo.packageType;
                                gui.addMessage("✓ Package " + completedPackage + " delivered successfully by " + agentName);

                                activePackages.remove(completedPackage);

                                // CRITICAL FIX: Always create new package after delivery
                                // Check if this was a rescue delivery
                                if (agentOnRescueMission.getOrDefault(agentName, false)) {
                                    // This agent was on rescue mission and just delivered the rescued package
                                    // Create package for RESCUED agent's type (the original owner)
                                    String rescuedAgentType = findRescuedAgentType(completedPackage);
                                    if (rescuedAgentType != null) {
                                        createNewPackage(rescuedAgentType);
                                        gui.addMessage("📦 Created replacement package for rescued agent type: " + rescuedAgentType);
                                    }
                                    // Mark rescue mission as complete
                                    agentOnRescueMission.put(agentName, false);
                                } else {
                                    // Normal delivery - create package for this agent's type
                                    createNewPackage(packageType);
                                }
                            }
                        }
                    } else if (content != null && content.startsWith("RESCUE_COMPLETE:")) {
                        String[] parts = content.split(":");
                        if (parts.length >= 3) {
                            String rescuedAgent = parts[1];
                            String packageName = parts[2];

                            gui.addMessage("✓ " + agentName + " successfully rescued " + rescuedAgent + " and took package " + packageName);

                            agentStates.put(agentName, "DELIVERING");
                            // Keep rescue flag true - will be cleared when delivery completes
                        }
                    }
                } else {
                    block();
                }
            }
        });

        // Listen for package pickups
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CONFIRM);
                ACLMessage msg = receive(mt);

                if (msg != null) {
                    String pickedUpPkg = msg.getContent();
                    String agentName = msg.getSender().getLocalName();

                    PackageInfo pkgInfo = activePackages.get(pickedUpPkg);
                    if (pkgInfo != null) {
                        pkgInfo.available = false;
                        pkgInfo.inDelivery = true;
                        pkgInfo.assignedAgent = agentName;
                        agentStates.put(agentName, "DELIVERING");

                        gui.addMessage(agentName + " picked up " + pickedUpPkg);
                        gui.removeMasterPackage(pickedUpPkg);
                    }
                } else {
                    block();
                }
            }
        });

        // Listen for agent refusals
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REFUSE);
                ACLMessage msg = receive(mt);

                if (msg != null) {
                    String content = msg.getContent();
                    String[] parts = content != null ? content.split(":") : new String[]{};

                    if (parts.length > 0 && parts[0].equals("OVERLOADED")) {
                        String refusedPkg = parts.length >= 2 ? parts[1] : "UNKNOWN";
                        String agentName = msg.getSender().getLocalName();

                        gui.addMessage("⚠️ " + agentName + " REFUSED " + refusedPkg + " due to overload");

                        if (!"UNKNOWN".equals(refusedPkg)) {
                            reassignPackage(refusedPkg, agentName);
                        }
                    }
                } else {
                    block();
                }
            }
        });

        // Start initial assignment
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                try {
                    Thread.sleep(2000);

                    for (int i = 1; i <= 3; i++) {
                        String agentName = "Agent" + i;
                        String packageType = agentPackageMapping.get(agentName);

                        assignNextAvailablePackage(agentName, packageType);
                        Thread.sleep(500);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        // Listen for agents requesting packages
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
                ACLMessage msg = receive(mt);

                if (msg != null) {
                    String agentName = msg.getSender().getLocalName();
                    String packageType = agentPackageMapping.get(agentName);

                    // Check if agent was on rescue mission
                    if (agentOnRescueMission.getOrDefault(agentName, false)) {
                        // Agent completed rescue mission, now generate delayed package
                        agentOnRescueMission.put(agentName, false);
                        gui.addMessage("📦 Generating delayed package for " + agentName + " after rescue mission");
                        createNewPackage(packageType);
                    }

                    // Assign next package
                    assignNextAvailablePackage(agentName, packageType);
                } else {
                    block();
                }
            }
        });
    }

    private void handleDistressCall(ACLMessage distressMsg) {
        String content = distressMsg.getContent();
        String[] parts = content.split(":");
        String strandedAgent = parts[1];
        String packageName = parts[2];
        int distanceFromMaster = Integer.parseInt(parts[3]);
        int remainingTime = Integer.parseInt(parts[4]);

        gui.addMessage("🚨 DISTRESS CALL received from " + strandedAgent);
        gui.addMessage("   → Package: " + packageName + ", Distance: " + distanceFromMaster + "s, Remaining: " + remainingTime + "s");
        gui.addMessage("   → Awaiting bids from agents...");

        // Store conversation ID for tracking bids
        String conversationId = distressMsg.getConversationId();
        rescueBids.put(conversationId, new ArrayList<>());

        // Wait 3 seconds for bids to come in, then evaluate
        addBehaviour(new WakerBehaviour(this, 3000) {
            @Override
            protected void onWake() {
                evaluateRescueBids(conversationId, strandedAgent, packageName, distanceFromMaster, remainingTime);
            }
        });
    }

    private void handleRescueBid(ACLMessage bidMsg) {
        String content = bidMsg.getContent();
        String[] parts = content.split(":");
        // RESCUE_BID:StrandedAgent:Package:BidTime:BidderAgent
        String strandedAgent = parts[1];
        String packageName = parts[2];
        int bidTime = Integer.parseInt(parts[3]);
        String bidderAgent = parts[4];
        String conversationId = bidMsg.getConversationId();

        gui.addMessage("   📊 Bid received: " + bidderAgent + " can rescue in " + bidTime + "s");

        // Store the bid
        List<RescueBid> bids = rescueBids.get(conversationId);
        if (bids != null) {
            bids.add(new RescueBid(bidderAgent, strandedAgent, packageName, bidTime, conversationId));
        }
    }

    private void evaluateRescueBids(String conversationId, String strandedAgent, String packageName,
                                    int distanceFromMaster, int remainingTime) {
        List<RescueBid> bids = rescueBids.get(conversationId);

        if (bids == null || bids.isEmpty()) {
            gui.addMessage("⚠️ No bids received for rescue of " + strandedAgent + ". Waiting...");
            return;
        }

        gui.addMessage("🔍 Evaluating " + bids.size() + " bids for rescue mission:");

        // Find best bid (lowest time)
        RescueBid bestBid = null;
        for (RescueBid bid : bids) {
            gui.addMessage("   → " + bid.agentName + ": " + bid.bidTime + "s");
            if (bestBid == null || bid.bidTime < bestBid.bidTime) {
                bestBid = bid;
            }
        }

        if (bestBid != null) {
            gui.addMessage("🏆 WINNER: " + bestBid.agentName + " wins with bid of " + bestBid.bidTime + "s");
            gui.addMessage("   → Awarding rescue mission to " + bestBid.agentName);

            // Mark agent as on rescue mission
            agentOnRescueMission.put(bestBid.agentName, true);

            // Calculate actual travel time (use a portion of the distance)
            int travelTime = 1 + (int) (Math.random() * distanceFromMaster);

            // Award the rescue mission
            ACLMessage award = new ACLMessage(ACLMessage.INFORM);
            award.setContent("RESCUE_AWARDED:" + strandedAgent + ":" + packageName + ":" +
                    travelTime + ":" + remainingTime);
            award.addReceiver(new AID(bestBid.agentName, AID.ISLOCALNAME));
            send(award);

            agentStates.put(bestBid.agentName, "RESCUE_MISSION");
        }

        // Clean up bids
        rescueBids.remove(conversationId);
    }

    private void createNewPackage(String packageType) {
        int counter = packageCounters.getOrDefault(packageType, 1);
        String packageName = packageType + "." + counter;
        int deliveryTime = 10 + (int) (Math.random() * 7);

        PackageInfo pkgInfo = new PackageInfo(packageName, packageType, deliveryTime);
        activePackages.put(packageName, pkgInfo);

        Queue<String> q = packageQueues.get(packageType);
        if (q != null) q.offer(packageName);

        gui.addMasterPackage(packageName, deliveryTime);
        gui.addMessage("📦 New package created: " + packageName + " (" + deliveryTime + "s) [Counter: " + counter + ", Queued]");

        packageCounters.put(packageType, counter + 1);
        notifyWaitingAgent(packageType);
    }

    private void notifyWaitingAgent(String packageType) {
        for (Map.Entry<String, String> entry : agentPackageMapping.entrySet()) {
            String agentName = entry.getKey();
            String agentPkgType = entry.getValue();

            if (agentPkgType.equals(packageType) && agentStates.get(agentName).equals("WAITING")) {
                assignNextAvailablePackage(agentName, packageType);
                break;
            }
        }
    }

    private void assignNextAvailablePackage(String agentName, String packageType) {
        Queue<String> queue = packageQueues.get(packageType);
        if (queue != null && !queue.isEmpty()) {
            String packageName = queue.poll();
            PackageInfo pkgInfo = activePackages.get(packageName);

            if (pkgInfo != null && pkgInfo.available) {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.setContent(packageName + ":" + pkgInfo.deliveryTime);
                msg.addReceiver(new AID(agentName, AID.ISLOCALNAME));
                send(msg);

                gui.addMessage("Assigned " + packageName + " (" + pkgInfo.deliveryTime + "s) to " + agentName + " [From Queue]");
                agentStates.put(agentName, "ASSIGNED");
            } else {
                gui.addMessage(agentName + " waiting for new " + packageType + ".X package...");
                agentStates.put(agentName, "WAITING");
            }
        } else {
            gui.addMessage(agentName + " waiting for new " + packageType + ".X package...");
            agentStates.put(agentName, "WAITING");
        }
    }

    private void reassignPackage(String pkg, String excludeAgent) {
        for (Map.Entry<String, String> entry : agentStates.entrySet()) {
            String agent = entry.getKey();
            String state = entry.getValue();

            if (agent.equals(excludeAgent)) continue;

            if (state.equals("IDLE") || state.equals("WAITING")) {
                PackageInfo pkgInfo = activePackages.get(pkg);
                if (pkgInfo != null) {
                    ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                    msg.setContent(pkg + ":" + pkgInfo.deliveryTime);
                    msg.addReceiver(new AID(agent, AID.ISLOCALNAME));
                    send(msg);

                    gui.addMessage("📦 Reassigned " + pkg + " to " + agent);
                    agentStates.put(agent, "ASSIGNED");
                }
                return;
            }
        }

        gui.addMessage("⚠️ No idle agents available for reassignment of " + pkg);
    }

    // Helper method to find the original package type from package name
    private String findRescuedAgentType(String packageName) {
        // Extract the type from package name (e.g., "1.1" -> "1")
        if (packageName != null && packageName.contains(".")) {
            return packageName.split("\\.")[0];
        }
        return null;
    }

    private void connectGUIButtons() {
        for (String agentName : agentPackageMapping.keySet()) {
            JButton[] buttons = gui.getAgentButtons(agentName);
            if (buttons != null && buttons.length >= 2) {
                JButton triggerButton = buttons[0];
                JButton recoverButton = buttons[1];

                // Remove old listeners
                for (ActionListener al : triggerButton.getActionListeners()) {
                    triggerButton.removeActionListener(al);
                }
                for (ActionListener al : recoverButton.getActionListeners()) {
                    recoverButton.removeActionListener(al);
                }

                // Hide/disable the second button (no longer needed)
                recoverButton.setVisible(false);

                final String agentNameFinal = agentName;
                triggerButton.addActionListener(e -> {
                    ACLMessage failureMsg = new ACLMessage(ACLMessage.REQUEST);
                    failureMsg.setContent("TRIGGER_FAILURE");
                    failureMsg.addReceiver(new AID(agentNameFinal, AID.ISLOCALNAME));
                    send(failureMsg);

                    gui.addMessage("💥 Manual failure triggered for " + agentNameFinal);
                });
            }
        }
        gui.addMessage("✅ GUI buttons connected to agents");
    }
}