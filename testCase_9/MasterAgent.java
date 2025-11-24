package testCase_3;

import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.*;

/**
 * MasterAgent.java
 *
 * Central coordinator for the delivery system with auction management.
 */
public class MasterAgent extends Agent {

    public static DeliveryGUI gui;

    private static final int NUM_AGENTS = 7;
    private static final int NUM_INITIAL_PACKAGES = 12;
    private static final int PACKAGE_REGENERATION_DELAY = 8000;
    private static final int AUCTION_WINDOW_MS = 2000;

    private static final int[] AGENT_PRIORITIES = {5, 4, 3, 3, 2, 2, 1};
    private static final int[] AGENT_CAPACITIES = {2, 2, 3, 1, 2, 1, 2};

    private static final String[][] PACKAGE_TYPES = {
            {"Urgent", "3", "10"},
            {"Standard", "7", "5"},
            {"Bulk", "12", "2"}
    };

    private Queue<PackageInfo> availablePackages;
    private Map<String, String> packagesInDelivery;
    private int packageCounter;
    private Map<String, Timer> regenerationTimers;

    private AuctionInfo currentAuction;
    private List<BidInfo> currentBids;
    private Timer auctionTimer;

    private Set<String> waitingAgents;

    private class PackageInfo {
        String name;
        int deliveryTime;
        String type;
        int urgencyScore;

        PackageInfo(String name, int deliveryTime, String type, int urgencyScore) {
            this.name = name;
            this.deliveryTime = deliveryTime;
            this.type = type;
            this.urgencyScore = urgencyScore;
        }
    }

    private class AuctionInfo {
        PackageInfo packageInfo;
        long startTime;
        long endTime;

        AuctionInfo(PackageInfo pkg, long startTime) {
            this.packageInfo = pkg;
            this.startTime = startTime;
            this.endTime = startTime + AUCTION_WINDOW_MS;
        }
    }

    private class BidInfo implements Comparable<BidInfo> {
        String agentName;
        int bidScore;
        int priority;
        long timestamp;

        BidInfo(String agentName, int bidScore, int priority, long timestamp) {
            this.agentName = agentName;
            this.bidScore = bidScore;
            this.priority = priority;
            this.timestamp = timestamp;
        }

        @Override
        public int compareTo(BidInfo other) {
            if (this.bidScore != other.bidScore) {
                return Integer.compare(other.bidScore, this.bidScore);
            }
            if (this.priority != other.priority) {
                return Integer.compare(other.priority, this.priority);
            }
            return Long.compare(this.timestamp, other.timestamp);
        }
    }

    @Override
    protected void setup() {
        availablePackages = new LinkedList<>();
        packagesInDelivery = new HashMap<>();
        regenerationTimers = new HashMap<>();
        currentBids = new ArrayList<>();
        waitingAgents = new HashSet<>();
        packageCounter = 1;
        currentAuction = null;

        SwingUtilities.invokeLater(() -> {
            gui = new DeliveryGUI();
            gui.addMessage("=============================================================");
            gui.addMessage("Master Agent Starting - Auction-Based Delivery System");
            gui.addMessage("=============================================================");
            gui.addMessage("Configuration: " + NUM_AGENTS + " agents, " + NUM_INITIAL_PACKAGES + " initial packages");
            gui.addMessage("Auction window: " + (AUCTION_WINDOW_MS/1000) + "s, Regeneration delay: " + (PACKAGE_REGENERATION_DELAY/1000) + "s");
            gui.addMessage("Trading enabled: true");
            gui.addMessage("");
        });

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                initializeSystem();
            }
        });

        addBehaviour(new MessageHandlerBehaviour());
    }

    private void initializeSystem() {
        SwingUtilities.invokeLater(() -> {
            gui.addMessage("--- Adding Agents to GUI ---");
            // Register all agents with GUI BEFORE they try to update
            for (int i = 1; i <= NUM_AGENTS; i++) {
                String name = "Agent" + i;
                int idx = i - 1;
                int p = (idx < AGENT_PRIORITIES.length) ? AGENT_PRIORITIES[idx] : 1;
                int c = (idx < AGENT_CAPACITIES.length) ? AGENT_CAPACITIES[idx] : 1;
                String display = name + " (P:" + p + ", Cap:" + c + ")";
                gui.addAgent(display);
            }
            gui.addMessage("All " + NUM_AGENTS + " agents registered with GUI");
            gui.addMessage("");
            gui.addMessage("--- Generating Initial Packages ---");
        });

        for (int i = 0; i < NUM_INITIAL_PACKAGES; i++) {
            createPackage();
        }

        SwingUtilities.invokeLater(() -> {
            gui.addMessage("");
            gui.addMessage("--- System Ready - Waiting for Agents ---");
            gui.addMessage("");
        });

        // Auctions will be triggered by agent REQUEST messages
        // No need to start auction automatically
    }

    private void createPackage() {
        double rand = Math.random();
        String[] selectedType;

        if (rand < 0.4) {
            selectedType = PACKAGE_TYPES[0];
        } else if (rand < 0.8) {
            selectedType = PACKAGE_TYPES[1];
        } else {
            selectedType = PACKAGE_TYPES[2];
        }

        String packageName = "P" + packageCounter++;
        int deliveryTime = Integer.parseInt(selectedType[1]);
        String type = selectedType[0];
        int urgencyScore = Integer.parseInt(selectedType[2]);

        PackageInfo newPackage = new PackageInfo(packageName, deliveryTime, type, urgencyScore);
        availablePackages.add(newPackage);

        final String pkgName = packageName;
        final int delTime = deliveryTime;
        final String pkgType = type;

        SwingUtilities.invokeLater(() -> {
            gui.addMessage("[PACKAGE CREATED] " + pkgName + " - Type: " + pkgType + ", Time: " + delTime + "s");
            gui.addAvailablePackage(pkgName, delTime);
        });
    }

    private void schedulePackageRegeneration(String originalName) {
        if (regenerationTimers.containsKey(originalName)) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            gui.addMessage("[REGENERATION SCHEDULED] " + originalName + " will regenerate in " +
                    (PACKAGE_REGENERATION_DELAY/1000) + "s");
        });

        Timer regenTimer = new Timer(PACKAGE_REGENERATION_DELAY, e -> {
            createPackage();

            SwingUtilities.invokeLater(() -> {
                gui.addMessage("[REGENERATION COMPLETE] New package available");
            });

            regenerationTimers.remove(originalName);

            if (currentAuction == null && !waitingAgents.isEmpty()) {
                addBehaviour(new jade.core.behaviours.WakerBehaviour(MasterAgent.this, 500) {
                    @Override
                    protected void onWake() {
                        startAuction();
                    }
                });
            }
        });

        regenTimer.setRepeats(false);
        regenTimer.start();
        regenerationTimers.put(originalName, regenTimer);
    }

    private void startAuction() {
        if (availablePackages.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                gui.addMessage("[AUCTION] No packages available - waiting for regeneration");
            });
            return;
        }

        if (currentAuction != null) {
            return;
        }

        PackageInfo packageToAuction = availablePackages.poll();

        SwingUtilities.invokeLater(() -> {
            gui.addMessage("");
            gui.addMessage("=============================================================");
            gui.addMessage("[AUCTION STARTED] Package: " + packageToAuction.name + " (" +
                    packageToAuction.type + ", " + packageToAuction.deliveryTime + "s)");
            gui.addMessage("Bidding window: " + (AUCTION_WINDOW_MS/1000) + " seconds");
            gui.addMessage("=============================================================");
        });

        currentAuction = new AuctionInfo(packageToAuction, System.currentTimeMillis());
        currentBids.clear();

        ACLMessage cfpMsg = new ACLMessage(ACLMessage.CFP);
        String content = packageToAuction.name + ":" + packageToAuction.deliveryTime + ":" +
                packageToAuction.type + ":" + packageToAuction.urgencyScore;
        cfpMsg.setContent(content);

        for (int i = 1; i <= NUM_AGENTS; i++) {
            cfpMsg.addReceiver(new AID("Agent" + i, AID.ISLOCALNAME));
        }

        System.out.println("MasterAgent: Sending CFP to " + NUM_AGENTS + " agents for package " + packageToAuction.name);
        send(cfpMsg);

        auctionTimer = new Timer(AUCTION_WINDOW_MS, e -> {
            endAuction();
        });
        auctionTimer.setRepeats(false);
        auctionTimer.start();
    }

    private void receiveBid(ACLMessage bidMessage) {
        if (currentAuction == null) {
            System.out.println("MasterAgent: Bid received but no active auction");
            return;
        }

        String[] parts = bidMessage.getContent().split(":");
        String packageName = parts[0];
        int bidScore = Integer.parseInt(parts[1]);
        int priority = Integer.parseInt(parts[2]);
        long timestamp = Long.parseLong(parts[3]);

        String agentName = bidMessage.getSender().getLocalName();

        if (!packageName.equals(currentAuction.packageInfo.name)) {
            System.out.println("MasterAgent: Bid for wrong package");
            return;
        }

        for (BidInfo bid : currentBids) {
            if (bid.agentName.equals(agentName)) {
                SwingUtilities.invokeLater(() -> {
                    gui.addMessage("[BID UPDATE] " + agentName + " updated bid to " + bidScore + " points");
                });
                bid.bidScore = bidScore;
                bid.timestamp = timestamp;
                return;
            }
        }

        BidInfo newBid = new BidInfo(agentName, bidScore, priority, timestamp);
        currentBids.add(newBid);

        final String agent = agentName;
        final int score = bidScore;
        final int prio = priority;

        SwingUtilities.invokeLater(() -> {
            gui.addMessage("[BID RECEIVED] " + agent + " (Priority: " + prio + ") - Score: " + score + " points");
        });
    }

    private void endAuction() {
        if (currentAuction == null) {
            return;
        }

        PackageInfo packageInfo = currentAuction.packageInfo;
        
        // Capture bid count NOW (not on EDT later)
        final int bidCount = currentBids.size();

        SwingUtilities.invokeLater(() -> {
            gui.addMessage("");
            gui.addMessage("[AUCTION ENDING] Evaluating " + bidCount + " bid(s)...");
        });

        if (currentBids.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                gui.addMessage("[AUCTION FAILED] No bids received for " + packageInfo.name);
                gui.addMessage("Package returned to queue");
            });

            availablePackages.add(packageInfo);
            currentAuction = null;

            addBehaviour(new jade.core.behaviours.WakerBehaviour(this, 2000) {
                @Override
                protected void onWake() {
                    startAuction();
                }
            });
            return;
        }

        Collections.sort(currentBids);

        BidInfo winner = currentBids.get(0);
        List<BidInfo> losers = currentBids.subList(1, currentBids.size());

        SwingUtilities.invokeLater(() -> {
            gui.addMessage("");
            gui.addMessage("-------------------------------------------------------------");
            gui.addMessage("[AUCTION WINNER] " + winner.agentName + " with " + winner.bidScore + " points");

            if (!losers.isEmpty()) {
                StringBuilder loserStr = new StringBuilder();
                for (BidInfo loser : losers) {
                    if (loserStr.length() > 0) loserStr.append(", ");
                    loserStr.append(loser.agentName).append("(").append(loser.bidScore).append(")");
                }
                gui.addMessage("[AUCTION LOSERS] " + loserStr.toString());
            }

            gui.addMessage("-------------------------------------------------------------");
            gui.addMessage("");
        });

        ACLMessage acceptMsg = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
        acceptMsg.setContent(packageInfo.name + ":" + packageInfo.deliveryTime + ":" + packageInfo.type);
        acceptMsg.addReceiver(new AID(winner.agentName, AID.ISLOCALNAME));
        send(acceptMsg);

        for (BidInfo loser : losers) {
            ACLMessage rejectMsg = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
            rejectMsg.setContent("Lost to " + winner.agentName + " (" + winner.bidScore + " vs " + loser.bidScore + ")");
            rejectMsg.addReceiver(new AID(loser.agentName, AID.ISLOCALNAME));
            send(rejectMsg);
        }

        final String pkgName = packageInfo.name;
        SwingUtilities.invokeLater(() -> {
            gui.removeAvailablePackage(pkgName);
        });

        currentAuction = null;
        currentBids.clear();

        if (!availablePackages.isEmpty()) {
            addBehaviour(new jade.core.behaviours.WakerBehaviour(this, 500) {
                @Override
                protected void onWake() {
                    startAuction();
                }
            });
        }
    }

    private class MessageHandlerBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();

            if (msg != null) {
                int performative = msg.getPerformative();
                String content = msg.getContent();
                String sender = msg.getSender().getLocalName();

                switch (performative) {
                    case ACLMessage.PROPOSE:
                        receiveBid(msg);
                        break;

                    case ACLMessage.CONFIRM:
                        handlePackagePickup(content, sender);
                        break;

                    case ACLMessage.REQUEST:
                        handlePackageRequest(sender);
                        break;

                    case ACLMessage.QUERY_REF:
                        handleTradeQuery(content, sender);
                        break;

                    case ACLMessage.INFORM:
                        // Check if it's a trade notification
                        if (msg.getConversationId() != null && 
                            msg.getConversationId().equals("TRADE_NOTIFICATION")) {
                            handleTradeNotification(content);
                        }
                        break;

                    default:
                        System.out.println("MasterAgent: Unknown message type from " + sender);
                }
            } else {
                block();
            }
        }
    }

    private void handlePackagePickup(String packageName, String agentName) {
        SwingUtilities.invokeLater(() -> {
            gui.addMessage("[PICKUP] " + agentName + " picked up " + packageName);
            gui.addDeliveringPackage(packageName, agentName);
        });

        packagesInDelivery.put(packageName, agentName);
    }

    private void handlePackageRequest(String agentName) {
        System.out.println("MasterAgent: Received REQUEST from " + agentName + 
                           ". Available packages: " + availablePackages.size() + 
                           ", Current auction: " + (currentAuction != null ? "active" : "none"));

        SwingUtilities.invokeLater(() -> {
            gui.addMessage("[REQUEST] " + agentName + " ready for package assignment");
        });

        List<String> completedPackages = new ArrayList<>();
        for (Map.Entry<String, String> entry : packagesInDelivery.entrySet()) {
            if (entry.getValue().equals(agentName)) {
                completedPackages.add(entry.getKey());
            }
        }

        for (String pkgName : completedPackages) {
            handleDeliveryComplete(pkgName, agentName);
        }

        waitingAgents.add(agentName);

        if (!availablePackages.isEmpty() && currentAuction == null) {
            System.out.println("MasterAgent: Starting auction in 500ms...");
            addBehaviour(new jade.core.behaviours.WakerBehaviour(MasterAgent.this, 500) {
                @Override
                protected void onWake() {
                    startAuction();
                }
            });
        }
    }

    private void handleDeliveryComplete(String packageName, String agentName) {
        SwingUtilities.invokeLater(() -> {
            gui.addMessage("[DELIVERY COMPLETE] " + agentName + " delivered " + packageName);
            gui.removeDeliveringPackage(packageName);
        });

        packagesInDelivery.remove(packageName);
        schedulePackageRegeneration(packageName);
    }

    private void handleTradeNotification(String content) {
        // Parse: "TRADE_COMPLETE:PackageName:OldAgent:NewAgent"
        String[] parts = content.split(":");
        if (parts.length < 4) return;
        
        String packageName = parts[1];
        String oldAgent = parts[2];
        String newAgent = parts[3];
        
        System.out.println("MasterAgent: *** TRADE NOTIFICATION *** Package " + packageName + 
                         " transferred from " + oldAgent + " to " + newAgent);
        
        // Update package tracking
        String currentOwner = packagesInDelivery.get(packageName);
        if (currentOwner != null) {
            System.out.println("MasterAgent: Updating tracking - " + packageName + 
                             " was with " + currentOwner + ", now with " + newAgent);
            packagesInDelivery.put(packageName, newAgent);
        } else {
            System.out.println("MasterAgent: Warning - Package " + packageName + 
                             " not found in delivery tracking, adding " + newAgent);
            packagesInDelivery.put(packageName, newAgent);
        }
        
        // Log in GUI
        SwingUtilities.invokeLater(() -> {
            gui.addMessage("[MASTER UPDATED] Package " + packageName + " now tracked under " + newAgent);
        });
    }

    private void handleTradeQuery(String content, String requesterName) {
        String[] parts = content.split(":");
        if (parts.length < 2) return;

        int requesterPriority = Integer.parseInt(parts[1]);
        int requesterPackages = parts.length >= 3 ? Integer.parseInt(parts[2]) : 0;
        
        System.out.println("MasterAgent: Trade query from " + requesterName + " (P:" + requesterPriority + ", Load:" + requesterPackages + ")");
        System.out.println("MasterAgent: Currently delivering: " + packagesInDelivery);

        StringBuilder opportunities = new StringBuilder();
        
        // Count packages per agent for workload analysis
        Map<String, Integer> agentWorkload = new HashMap<>();
        for (String agentName : packagesInDelivery.values()) {
            agentWorkload.put(agentName, agentWorkload.getOrDefault(agentName, 0) + 1);
        }
        
        for (Map.Entry<String, String> entry : packagesInDelivery.entrySet()) {
            String agentName = entry.getValue();
            String packageName = entry.getKey();

            int agentIndex = Integer.parseInt(agentName.replace("Agent", "")) - 1;
            int agentPriority = (agentIndex < AGENT_PRIORITIES.length) ?
                    AGENT_PRIORITIES[agentIndex] : 1;
            int agentPackages = agentWorkload.getOrDefault(agentName, 1);

            int priorityDiff = requesterPriority - agentPriority;
            int workloadDiff = agentPackages - requesterPackages;
            
            System.out.println("MasterAgent:   Checking " + agentName + " (P:" + agentPriority + ", Load:" + agentPackages + 
                             ") with " + packageName);
            System.out.println("MasterAgent:     Priority diff: " + priorityDiff + ", Workload diff: " + workloadDiff);

            // Match if priority advantage OR workload imbalance
            boolean priorityMatch = priorityDiff >= 2;
            boolean workloadMatch = workloadDiff >= 2;
            
            if (priorityMatch || workloadMatch) {
                String reason = priorityMatch ? "priority" : "workload";
                System.out.println("MasterAgent:   ✓ MATCH (" + reason + ")! Adding to opportunities");
                if (opportunities.length() > 0) opportunities.append(",");
                opportunities.append(agentName).append(":").append(packageName);
            } else {
                System.out.println("MasterAgent:   ✗ No match (priority diff: " + priorityDiff + ", workload diff: " + workloadDiff + ")");
            }
        }

        System.out.println("MasterAgent: Sending opportunities: " + opportunities.toString());

        ACLMessage response = new ACLMessage(ACLMessage.INFORM);
        response.setContent("TRADE_OPPORTUNITIES:" + opportunities.toString());
        response.addReceiver(new AID(requesterName, AID.ISLOCALNAME));
        send(response);
    }

    @Override
    protected void takeDown() {
        if (auctionTimer != null) {
            auctionTimer.stop();
        }

        for (Timer timer : regenerationTimers.values()) {
            timer.stop();
        }

        System.out.println("MasterAgent: Shutting down");
    }
}
