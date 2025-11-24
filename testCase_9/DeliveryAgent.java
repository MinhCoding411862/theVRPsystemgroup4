package testCase_3;

import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.core.behaviours.CyclicBehaviour;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.ArrayList;
import java.util.List;

/**
 * DeliveryAgent.java
 *
 * Enhanced Delivery Agent with auction bidding and trading capabilities.
 */
public class DeliveryAgent extends Agent {

    // ============================================================
    // AGENT PROPERTIES
    // ============================================================

    private int priority;
    private int maxCapacity;
    private String displayName;
    private int agentIndex;

    // ============================================================
    // STATE TRACKING
    // ============================================================

    private List<PackageInfo> currentPackages;
    private int timeToMaster;
    private String state;
    private boolean inAuction;
    private Timer deliveryTimer;
    private Timer returnTimer;
    
    // Trade lock to prevent simultaneous trades
    private boolean currentlyTrading = false;
    private String packageBeingTraded = null;

    // ============================================================
    // CONFIGURATION CONSTANTS
    // ============================================================

    private static final int[] AGENT_PRIORITIES = {5, 4, 3, 3, 2, 2, 1};
    private static final int[] AGENT_CAPACITIES = {2, 2, 3, 1, 2, 1, 2};

    private static final int PRIORITY_WEIGHT = 20;
    private static final int CAPACITY_WEIGHT = 10;
    private static final int DISTANCE_WEIGHT = 2;
    private static final int URGENCY_WEIGHT = 1;

    private static final boolean TRADING_ENABLED = true;
    private static final int MIN_PRIORITY_DIFF_FOR_TRADE = 2;

    // ============================================================
    // HELPER CLASS
    // ============================================================

    private class PackageInfo {
        String name;
        int deliveryTime;
        int remainingTime;
        String type;

        PackageInfo(String name, int deliveryTime, String type) {
            this.name = name;
            this.deliveryTime = deliveryTime;
            this.remainingTime = deliveryTime;
            this.type = type;
        }
    }

    // ============================================================
    // AGENT SETUP
    // ============================================================

    @Override
    protected void setup() {
        String agentName = getLocalName();
        this.agentIndex = Integer.parseInt(agentName.replace("Agent", "")) - 1;

        this.priority = (agentIndex < AGENT_PRIORITIES.length) ?
                AGENT_PRIORITIES[agentIndex] : 1;
        this.maxCapacity = (agentIndex < AGENT_CAPACITIES.length) ?
                AGENT_CAPACITIES[agentIndex] : 1;

        // FIXED: Display name format matches what MasterAgent creates
        this.displayName = agentName + " (P:" + priority + ", Cap:" + maxCapacity + ")";

        this.currentPackages = new ArrayList<>();
        this.timeToMaster = 0;
        this.state = "IDLE";
        this.inAuction = false;

        System.out.println(displayName + " initialized and ready");

        addBehaviour(new MessageHandlerBehaviour());

        // Wait for GUI to be fully initialized by MasterAgent, then request first package
        addBehaviour(new jade.core.behaviours.WakerBehaviour(this, 1500) {
            @Override
            protected void onWake() {
                System.out.println(displayName + " requesting package from Master");
                requestPackage();
            }
        });
    }

    // ============================================================
    // MESSAGE HANDLING BEHAVIOR
    // ============================================================

    private class MessageHandlerBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();

            if (msg != null) {
                int performative = msg.getPerformative();
                String content = msg.getContent();
                String sender = msg.getSender().getLocalName();

                System.out.println(getLocalName() + ": Received message type " + ACLMessage.getPerformative(performative) + " from " + sender);

                switch (performative) {
                    case ACLMessage.CFP:
                        handleAuctionCall(content);
                        break;
                    case ACLMessage.ACCEPT_PROPOSAL:
                        handleAuctionWin(content);
                        break;
                    case ACLMessage.REJECT_PROPOSAL:
                        handleAuctionLoss(content);
                        break;
                    case ACLMessage.PROPOSE:
                        handleTradeRequest(content, sender);
                        break;
                    case ACLMessage.AGREE:
                        handleTradeAccepted(content);
                        break;
                    case ACLMessage.REFUSE:
                        handleTradeRefused(content);
                        break;
                    case ACLMessage.INFORM:
                        handleInformMessage(content);
                        break;
                    default:
                        System.out.println(getLocalName() + ": Unknown message type " + performative);
                }
            } else {
                block();
            }
        }
    }

    // ============================================================
    // AUCTION BIDDING LOGIC
    // ============================================================

    private void handleAuctionCall(String auctionData) {
        System.out.println(getLocalName() + ": handleAuctionCall called with data: " + auctionData);
        System.out.println(getLocalName() + ": State check - packages: " + currentPackages.size() + "/" + maxCapacity + 
                           ", timeToMaster: " + timeToMaster + ", state: " + state);

        if (currentPackages.size() >= maxCapacity) {
            System.out.println(getLocalName() + ": Cannot bid - at full capacity");
            return;
        }

        if (timeToMaster > 0) {
            System.out.println(getLocalName() + ": Cannot bid - not at master (timeToMaster=" + timeToMaster + ")");
            return;
        }

        if (state.equals("DELIVERING")) {
            System.out.println(getLocalName() + ": Cannot bid - currently delivering");
            return;
        }

        String[] parts = auctionData.split(":");
        String packageName = parts[0];
        int deliveryTime = Integer.parseInt(parts[1]);
        String packageType = parts[2];
        int urgencyScore = Integer.parseInt(parts[3]);

        int bidScore = calculateBid(urgencyScore);

        System.out.println(getLocalName() + ": SENDING BID " + bidScore + " for " + packageName);

        ACLMessage bidMsg = new ACLMessage(ACLMessage.PROPOSE);
        bidMsg.setContent(packageName + ":" + bidScore + ":" + priority + ":" + System.currentTimeMillis());
        bidMsg.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
        send(bidMsg);

        System.out.println(getLocalName() + ": Bid sent successfully");

        inAuction = true;
        state = "BIDDING";
    }

    private int calculateBid(int urgencyScore) {
        int priorityComponent = priority * PRIORITY_WEIGHT;
        int capacityComponent = (maxCapacity - currentPackages.size()) * CAPACITY_WEIGHT;
        int distancePenalty = timeToMaster * DISTANCE_WEIGHT;
        int urgencyBonus = urgencyScore * URGENCY_WEIGHT;

        int totalScore = priorityComponent + capacityComponent - distancePenalty + urgencyBonus;
        return Math.max(0, totalScore);
    }

    private void handleAuctionWin(String assignmentData) {
        inAuction = false;

        System.out.println(getLocalName() + ": WON auction - " + assignmentData);

        String[] parts = assignmentData.split(":");
        String packageName = parts[0];
        int deliveryTime = Integer.parseInt(parts[1]);
        String packageType = parts[2];

        currentPackages.add(new PackageInfo(packageName, deliveryTime, packageType));

        updateGUI();

        ACLMessage pickupMsg = new ACLMessage(ACLMessage.CONFIRM);
        pickupMsg.setContent(packageName);
        pickupMsg.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
        send(pickupMsg);

        if (!state.equals("DELIVERING")) {
            startDelivery();
        }
    }

    private void handleAuctionLoss(String lossData) {
        inAuction = false;
        state = "IDLE";

        System.out.println(getLocalName() + ": Lost auction - " + lossData);
        System.out.println(getLocalName() + ": Current state - Packages: " + currentPackages.size() + 
                         "/" + maxCapacity + ", Trading enabled: " + TRADING_ENABLED);

        // Try to get work through trading if we have capacity
        if (TRADING_ENABLED && currentPackages.size() < maxCapacity) {
            System.out.println(getLocalName() + ": ✓ Conditions met - attempting trade immediately");
            // Try trade immediately (no delay)
            considerTradeRequest();
        } else {
            System.out.println(getLocalName() + ": ✗ Cannot trade - Trading enabled: " + TRADING_ENABLED + 
                             ", Has capacity: " + (currentPackages.size() < maxCapacity));
        }
    }

    // ============================================================
    // DELIVERY EXECUTION
    // ============================================================

    private void startDelivery() {
        state = "DELIVERING";

        System.out.println(getLocalName() + ": Starting delivery of " + currentPackages.size() + " package(s)");

        deliveryTimer = new Timer(1000, e -> {
            boolean allDelivered = true;

            for (PackageInfo pkg : currentPackages) {
                if (pkg.remainingTime > 0) {
                    pkg.remainingTime--;
                    allDelivered = false;
                }
            }

            updateGUI();

            if (allDelivered) {
                completeDelivery();
            }
        });
        deliveryTimer.start();
    }

    private void completeDelivery() {
        if (deliveryTimer != null) {
            deliveryTimer.stop();
            deliveryTimer = null;
        }

        int maxDeliveryTime = 0;
        StringBuilder packageNames = new StringBuilder();

        for (PackageInfo pkg : currentPackages) {
            if (pkg.deliveryTime > maxDeliveryTime) {
                maxDeliveryTime = pkg.deliveryTime;
            }
            if (packageNames.length() > 0) packageNames.append(", ");
            packageNames.append(pkg.name);
        }

        timeToMaster = maxDeliveryTime;

        System.out.println(getLocalName() + ": Completed delivery, returning (" + timeToMaster + "s)");

        currentPackages.clear();
        updateGUI();
        startReturn();
    }

    private void startReturn() {
        state = "RETURNING";

        returnTimer = new Timer(1000, e -> {
            timeToMaster--;
            updateGUI();

            if (timeToMaster <= 0) {
                arriveAtMaster();
            }
        });
        returnTimer.start();
    }

    private void arriveAtMaster() {
        if (returnTimer != null) {
            returnTimer.stop();
            returnTimer = null;
        }

        timeToMaster = 0;
        state = "IDLE";

        System.out.println(getLocalName() + ": Arrived at master");

        updateGUI();
        
        // Only request if not already in auction or trading
        if (!inAuction && !currentlyTrading) {
            requestPackage();
        }
    }

    private void requestPackage() {
        // Prevent duplicate requests
        if (inAuction || currentlyTrading) {
            System.out.println(getLocalName() + ": Skipping REQUEST - already in auction or trading");
            return;
        }
        
        ACLMessage requestMsg = new ACLMessage(ACLMessage.REQUEST);
        requestMsg.setContent("READY_FOR_PACKAGE");
        requestMsg.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
        send(requestMsg);
    }

    // ============================================================
    // TRADING / NEGOTIATION LOGIC
    // ============================================================

    private void considerTradeRequest() {
        // Only check if we have capacity for more packages
        if (currentPackages.size() >= maxCapacity) {
            System.out.println(getLocalName() + ": Cannot request trade - at full capacity");
            return;
        }

        // Set state to TRADING and hold for visibility
        state = "TRADING";
        updateGUI();

        System.out.println(getLocalName() + ": *** ENTERING TRADING MODE *** Requesting trade opportunities from Master");
        System.out.println(getLocalName() + ": My workload: " + currentPackages.size() + "/" + maxCapacity);

        // Add 1 second delay before sending request (so TRADING status is visible)
        addBehaviour(new jade.core.behaviours.WakerBehaviour(this, 1000) {
            @Override
            protected void onWake() {
                // Include current package count for workload balancing
                ACLMessage tradeInfoRequest = new ACLMessage(ACLMessage.QUERY_REF);
                tradeInfoRequest.setContent("REQUEST_TRADE_OPPORTUNITIES:" + priority + ":" + currentPackages.size());
                tradeInfoRequest.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
                send(tradeInfoRequest);
            }
        });
    }

    private void handleTradeRequest(String tradeData, String requesterName) {
        String[] parts = tradeData.split(":");
        String requestedPackage = parts[0];
        int requesterPriority = Integer.parseInt(parts[1]);
        int requesterPackages = parts.length >= 3 ? Integer.parseInt(parts[2]) : 0;

        System.out.println(getLocalName() + ": *** TRADE REQUEST RECEIVED from " + requesterName + " ***");
        System.out.println(getLocalName() + ": Workload comparison - Requester: " + requesterPackages + ", Me: " + currentPackages.size());
        
        // FIX 1: Reject if already trading
        if (currentlyTrading) {
            System.out.println(getLocalName() + ": REJECTING - Already in trade negotiation with another agent");
            ACLMessage refuseMsg = new ACLMessage(ACLMessage.REFUSE);
            refuseMsg.setContent("Already trading with another agent");
            refuseMsg.addReceiver(new AID(requesterName, AID.ISLOCALNAME));
            send(refuseMsg);
            return;
        }
        
        // FIX 2: Reject if this specific package is being traded
        if (packageBeingTraded != null && packageBeingTraded.equals(requestedPackage)) {
            System.out.println(getLocalName() + ": REJECTING - Package " + requestedPackage + " is already being traded");
            ACLMessage refuseMsg = new ACLMessage(ACLMessage.REFUSE);
            refuseMsg.setContent("Package already being traded");
            refuseMsg.addReceiver(new AID(requesterName, AID.ISLOCALNAME));
            send(refuseMsg);
            return;
        }
        
        // Pause to make trade visible
        String previousState = state;
        state = "TRADING";
        updateGUI();

        PackageInfo foundPackage = currentPackages.stream().filter(pkg -> pkg.name.equals(requestedPackage)).findFirst().orElse(null);

        if (foundPackage == null) {
            System.out.println(getLocalName() + ": Cannot trade - package " + requestedPackage + " not found");
            state = previousState;
            updateGUI();
            
            ACLMessage refuseMsg = new ACLMessage(ACLMessage.REFUSE);
            refuseMsg.setContent("Package not found");
            refuseMsg.addReceiver(new AID(requesterName, AID.ISLOCALNAME));
            send(refuseMsg);
            return;
        }
        
        // FIX 3: Reject if package already delivered (0 seconds remaining)
        if (foundPackage.remainingTime <= 0) {
            System.out.println(getLocalName() + ": Cannot trade - package " + requestedPackage + " already delivered (0s remaining)");
            state = previousState;
            updateGUI();
            
            ACLMessage refuseMsg = new ACLMessage(ACLMessage.REFUSE);
            refuseMsg.setContent("Package already delivered");
            refuseMsg.addReceiver(new AID(requesterName, AID.ISLOCALNAME));
            send(refuseMsg);
            return;
        }

        // Lock this trade
        currentlyTrading = true;
        packageBeingTraded = requestedPackage;
        
        boolean shouldAcceptTrade = evaluateTrade(requesterPriority, requesterPackages);

        if (shouldAcceptTrade) {
            final PackageInfo pkgToTrade = foundPackage;
            final String finalRequesterName = requesterName;
            final int finalRequesterPriority = requesterPriority;
            
            // Use WakerBehaviour for delay instead of Thread.sleep
            addBehaviour(new jade.core.behaviours.WakerBehaviour(DeliveryAgent.this, 1500) {
                @Override
                protected void onWake() {
                    // FIX 4: Verify package still exists before executing trade
                    if (!currentPackages.contains(pkgToTrade)) {
                        System.out.println(getLocalName() + ": TRADE CANCELLED - Package " + requestedPackage + " no longer available");
                        
                        ACLMessage refuseMsg = new ACLMessage(ACLMessage.REFUSE);
                        refuseMsg.setContent("Package no longer available");
                        refuseMsg.addReceiver(new AID(finalRequesterName, AID.ISLOCALNAME));
                        send(refuseMsg);
                        
                        // Unlock trade
                        currentlyTrading = false;
                        packageBeingTraded = null;
                        state = previousState;
                        updateGUI();
                        return;
                    }
                    
                    // FIX 5: Verify package not delivered during delay
                    if (pkgToTrade.remainingTime <= 0) {
                        System.out.println(getLocalName() + ": TRADE CANCELLED - Package " + requestedPackage + " was delivered during negotiation");
                        
                        ACLMessage refuseMsg = new ACLMessage(ACLMessage.REFUSE);
                        refuseMsg.setContent("Package was delivered during negotiation");
                        refuseMsg.addReceiver(new AID(finalRequesterName, AID.ISLOCALNAME));
                        send(refuseMsg);
                        
                        // Unlock trade
                        currentlyTrading = false;
                        packageBeingTraded = null;
                        state = previousState;
                        updateGUI();
                        return;
                    }
                    
                    // Execute trade
                    currentPackages.remove(pkgToTrade);

                    System.out.println(getLocalName() + ": *** TRADE ACCEPTED *** - giving " + requestedPackage + " to " + finalRequesterName);

                    // Log trade in GUI with emphasis
                    SwingUtilities.invokeLater(() -> {
                        if (MasterAgent.gui != null) {
                            MasterAgent.gui.addMessage("═══════════════════════════════════════════════════════");
                            MasterAgent.gui.addMessage("[TRADE EXECUTED] " + getLocalName() + " → " + finalRequesterName);
                            MasterAgent.gui.addMessage("Package: " + requestedPackage + " (" + pkgToTrade.remainingTime + "s remaining)");
                            MasterAgent.gui.addMessage("Reason: Priority-based transfer (P:" + priority + " → P:" + finalRequesterPriority + ")");
                            MasterAgent.gui.addMessage("═══════════════════════════════════════════════════════");
                        }
                    });

                    // Send package to requester
                    ACLMessage acceptMsg = new ACLMessage(ACLMessage.AGREE);
                    acceptMsg.setContent(pkgToTrade.name + ":" + pkgToTrade.remainingTime + ":" + pkgToTrade.type);
                    acceptMsg.addReceiver(new AID(finalRequesterName, AID.ISLOCALNAME));
                    send(acceptMsg);

                    // NOTIFY MASTER AGENT OF TRADE
                    ACLMessage notifyMaster = new ACLMessage(ACLMessage.INFORM);
                    notifyMaster.setConversationId("TRADE_NOTIFICATION");
                    notifyMaster.setContent("TRADE_COMPLETE:" + pkgToTrade.name + ":" + 
                                           getLocalName() + ":" + finalRequesterName);
                    notifyMaster.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
                    send(notifyMaster);
                    
                    System.out.println(getLocalName() + ": Notified MasterAgent of trade: " + 
                                     pkgToTrade.name + " → " + finalRequesterName);

                    // Unlock trade
                    currentlyTrading = false;
                    packageBeingTraded = null;
                    
                    // Return to previous state
                    state = previousState;
                    updateGUI();
                }
            });
        } else {
            System.out.println(getLocalName() + ": *** TRADE REFUSED *** with " + requesterName);

            // Unlock trade
            currentlyTrading = false;
            packageBeingTraded = null;
            
            // Return to previous state
            state = previousState;
            updateGUI();

            ACLMessage refuseMsg = new ACLMessage(ACLMessage.REFUSE);
            refuseMsg.setContent("Trade not beneficial (priority diff: " + (requesterPriority - priority) + ")");
            refuseMsg.addReceiver(new AID(requesterName, AID.ISLOCALNAME));
            send(refuseMsg);
        }
    }

    private boolean evaluateTrade(int requesterPriority, int requesterPackages) {
        int priorityDiff = requesterPriority - this.priority;
        int workloadDiff = currentPackages.size() - requesterPackages;
        
        // Accept trade if:
        // 1. Requester has significant priority advantage (≥2 levels)
        // 2. OR significant workload imbalance (I have ≥2 more packages)
        
        boolean priorityBased = priorityDiff >= MIN_PRIORITY_DIFF_FOR_TRADE && currentPackages.size() > 0;
        boolean workloadBased = workloadDiff >= 2;  // I'm overloaded, they need work
        
        if (priorityBased) {
            System.out.println(getLocalName() + ": Trade evaluation - PRIORITY-BASED");
            System.out.println(getLocalName() + ":   Priority diff: " + priorityDiff + " (≥" + MIN_PRIORITY_DIFF_FOR_TRADE + ") ✓");
            System.out.println(getLocalName() + ":   Decision: ACCEPTING");
            return true;
        }
        
        if (workloadBased) {
            System.out.println(getLocalName() + ": Trade evaluation - WORKLOAD-BASED");
            System.out.println(getLocalName() + ":   Workload diff: " + workloadDiff + " (≥2) ✓");
            System.out.println(getLocalName() + ":   I have " + currentPackages.size() + ", they have " + requesterPackages);
            System.out.println(getLocalName() + ":   Decision: ACCEPTING (balancing workload)");
            return true;
        }
        
        System.out.println(getLocalName() + ": Trade evaluation - REFUSING");
        System.out.println(getLocalName() + ":   Priority diff: " + priorityDiff + " (need ≥" + MIN_PRIORITY_DIFF_FOR_TRADE + ")");
        System.out.println(getLocalName() + ":   Workload diff: " + workloadDiff + " (need ≥2)");
        return false;
    }

    private void handleTradeAccepted(String packageData) {
        String[] parts = packageData.split(":");
        String packageName = parts[0];
        int remainingTime = Integer.parseInt(parts[1]);
        String packageType = parts[2];

        System.out.println(getLocalName() + ": *** TRADE COMPLETED *** - received " + packageName + " with " + remainingTime + "s remaining");

        // Log successful trade with emphasis
        SwingUtilities.invokeLater(() -> {
            if (MasterAgent.gui != null) {
                MasterAgent.gui.addMessage("[TRADE COMPLETE] " + getLocalName() + 
                                          " now has " + packageName + " and will continue delivery");
            }
        });

        PackageInfo tradedPackage = new PackageInfo(packageName, remainingTime, packageType);
        tradedPackage.remainingTime = remainingTime;
        currentPackages.add(tradedPackage);

        // Return to appropriate state
        state = "IDLE";
        updateGUI();

        if (!state.equals("DELIVERING")) {
            startDelivery();
        }
    }

    private void handleTradeRefused(String reason) {
        System.out.println(getLocalName() + ": Trade refused - " + reason);
        
        // Log trade failure
        SwingUtilities.invokeLater(() -> {
            if (MasterAgent.gui != null) {
                MasterAgent.gui.addMessage("[TRADE REFUSED] " + getLocalName() + " - " + reason);
            }
        });
        
        state = "IDLE";
    }

    private void handleInformMessage(String content) {
        System.out.println(getLocalName() + ": Received info - " + content);
        
        // Handle trade opportunities from MasterAgent
        if (content.startsWith("TRADE_OPPORTUNITIES:")) {
            String opportunities = content.substring("TRADE_OPPORTUNITIES:".length());
            
            if (opportunities.isEmpty()) {
                System.out.println(getLocalName() + ": No trade opportunities available");
                
                // Stay in TRADING state for 2 seconds so it's visible
                addBehaviour(new jade.core.behaviours.WakerBehaviour(DeliveryAgent.this, 2000) {
                    @Override
                    protected void onWake() {
                        SwingUtilities.invokeLater(() -> {
                            if (MasterAgent.gui != null) {
                                MasterAgent.gui.addMessage("[TRADE] " + getLocalName() + 
                                                          " - No suitable trade partners found");
                            }
                        });
                        // Return to IDLE state after delay
                        state = "IDLE";
                        updateGUI();
                    }
                });
                return;
            }
            
            // Parse opportunities: "Agent7:P3,Agent6:P5"
            String[] opps = opportunities.split(",");
            for (String opp : opps) {
                String[] parts = opp.split(":");
                if (parts.length == 2) {
                    String targetAgent = parts[0];
                    String packageName = parts[1];
                    
                    System.out.println(getLocalName() + ": *** INITIATING TRADE *** for " + packageName + " from " + targetAgent);
                    
                    final String finalTargetAgent = targetAgent;
                    final String finalPackageName = packageName;
                    
                    SwingUtilities.invokeLater(() -> {
                        if (MasterAgent.gui != null) {
                            MasterAgent.gui.addMessage("───────────────────────────────────────────────────────");
                            MasterAgent.gui.addMessage("[TRADE NEGOTIATION] " + getLocalName() + 
                                                      " → " + finalTargetAgent + ": Requesting " + finalPackageName);
                            MasterAgent.gui.addMessage("───────────────────────────────────────────────────────");
                        }
                    });
                    
                    // Use WakerBehaviour for delay instead of Thread.sleep
                    addBehaviour(new jade.core.behaviours.WakerBehaviour(DeliveryAgent.this, 1000) {
                        @Override
                        protected void onWake() {
                            // Send trade request to target agent (include package count for workload evaluation)
                            ACLMessage tradeRequest = new ACLMessage(ACLMessage.PROPOSE);
                            tradeRequest.setContent(finalPackageName + ":" + priority + ":" + currentPackages.size());
                            tradeRequest.addReceiver(new AID(finalTargetAgent, AID.ISLOCALNAME));
                            send(tradeRequest);
                        }
                    });
                    
                    // Only request one trade at a time
                    break;
                }
            }
        }
    }

    // ============================================================
    // GUI UPDATE
    // ============================================================

    private void updateGUI() {
        SwingUtilities.invokeLater(() -> {
            if (MasterAgent.gui == null) return;

            int totalDeliveryTime = 0;
            StringBuilder packageList = new StringBuilder();

            for (PackageInfo pkg : currentPackages) {
                totalDeliveryTime += pkg.remainingTime;
                if (packageList.length() > 0) packageList.append(", ");
                packageList.append(pkg.name);
            }

            MasterAgent.gui.updateAgentTimes(displayName, timeToMaster, totalDeliveryTime);

            String pkgDisplay = packageList.length() > 0 ? packageList.toString() : null;
            MasterAgent.gui.updateAgentPackage(displayName, pkgDisplay);

            MasterAgent.gui.updateAgentCapacity(displayName, currentPackages.size(), maxCapacity);
            
            // Update status with special color for TRADING
            MasterAgent.gui.updateAgentStatus(displayName, state);
        });
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    @Override
    protected void takeDown() {
        if (deliveryTimer != null) {
            deliveryTimer.stop();
        }
        if (returnTimer != null) {
            returnTimer.stop();
        }

        System.out.println(getLocalName() + ": Agent terminating");
    }
}
