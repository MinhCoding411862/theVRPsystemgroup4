package testCase_6;
import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import javax.swing.*;

public class DeliveryAgent extends Agent {
    private int currentTimeToMaster = 0;
    private String currentPackage = null;
    private int consecutiveDeliveries = 0;
    private String agentState = "IDLE"; // IDLE, DELIVERING, RETURNING, WAITING_RESCUE, RESCUE
    private Timer currentTimer = null;
    private int savedDeliveryTime = 0;
    private int currentDeliveryTimeRemaining = 0;
    private String assignedPackageType = null; // e.g., "1", "2", "3"
    private boolean isWaitingForRescue = false;
    private int currentDistanceFromMaster = 0; // Track current distance during delivery
    private PendingRescueMission pendingRescue = null;

    private class PendingRescueMission {
        String failedAgent;
        String pkg;
        int travelTime;
        int remainingDeliveryTime;

        PendingRescueMission(String failedAgent, String pkg, int travelTime, int remainingDeliveryTime) {
            this.failedAgent = failedAgent;
            this.pkg = pkg;
            this.travelTime = travelTime;
            this.remainingDeliveryTime = remainingDeliveryTime;
        }
    }

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            assignedPackageType = (String) args[0];
        }

        System.out.println(getLocalName() + " ready to receive packages. Assigned type: " + assignedPackageType);

        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            @Override
            public void action() {
                // Receive messages when at master
                if (currentTimeToMaster == 0 && agentState.equals("IDLE")) {
                    MessageTemplate mt = MessageTemplate.or(
                            MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                            MessageTemplate.MatchPerformative(ACLMessage.REQUEST)
                    );
                    ACLMessage msg = receive(mt);

                    if (msg != null) {
                        String content = msg.getContent();

                        // Handle manual failure trigger
                        if (content != null && content.equals("TRIGGER_FAILURE")) {
                            // Ignore if not delivering
                            return;
                        }

                        // Normal package assignment (not rescue awards which are handled elsewhere)
                        if (content != null && !content.startsWith("RESCUE_AWARDED:")) {
                            // Normal package assignment
                            String[] parts = content.split(":");
                            String pkg = parts[0];
                            int deliveryTime = Integer.parseInt(parts[1]);

                            // Check if overloaded
                            if (consecutiveDeliveries >= 3) {
                                ACLMessage refuse = new ACLMessage(ACLMessage.REFUSE);
                                refuse.setContent("OVERLOADED:" + pkg);
                                refuse.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
                                send(refuse);

                                SwingUtilities.invokeLater(() -> {
                                    MasterAgent.gui.addMessage(getLocalName() + " REFUSED " + pkg + " - OVERLOADED (cooldown required)");
                                    MasterAgent.gui.updateAgentStatus(getLocalName(), "Overloaded - Cooldown");
                                });

                                // Start cooldown
                                startCooldown();
                                return;
                            }

                            currentPackage = pkg;
                            agentState = "DELIVERING";

                            // Notify master that package was picked up
                            ACLMessage pickupMsg = new ACLMessage(ACLMessage.CONFIRM);
                            pickupMsg.setContent(pkg);
                            pickupMsg.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
                            send(pickupMsg);

                            SwingUtilities.invokeLater(() -> {
                                MasterAgent.gui.updateAgentPackage(getLocalName(), pkg);
                                MasterAgent.gui.updateAgentTimes(getLocalName(), 0, deliveryTime);
                                MasterAgent.gui.updateConsecutiveDeliveries(getLocalName(), consecutiveDeliveries);
                            });

                            // Start delivery process
                            startDelivery(pkg, deliveryTime);
                        }
                    } else {
                        block();
                    }
                } else {
                    block();
                }
            }
        });

        // Listen for distress calls from other agents
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
                ACLMessage msg = myAgent.receive(mt);

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

        // Listen for rescue mission awards (can interrupt return journey)
        // THIS IS THE ONLY RESCUE_AWARDED HANDLER - NO DUPLICATES!
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            @Override
            public void action() {
                // Create a specific template that ONLY matches RESCUE_AWARDED messages
                MessageTemplate mt = MessageTemplate.and(
                        MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                        new MessageTemplate(new MessageTemplate.MatchExpression() {
                            public boolean match(ACLMessage msg) {
                                String content = msg.getContent();
                                return content != null && content.startsWith("RESCUE_AWARDED:");
                            }
                        })
                );

                ACLMessage msg = receive(mt);

                if (msg != null) {
                    String content = msg.getContent();
                    String[] parts = content.split(":");
                    String failedAgent = parts[1];
                    String pkg = parts[2];
                    int travelTime = Integer.parseInt(parts[3]);
                    int remainingDeliveryTime = Integer.parseInt(parts[4]);

                    // If currently DELIVERING, queue the rescue mission for later
                    if (agentState.equals("DELIVERING")) {
                        pendingRescue = new PendingRescueMission(failedAgent, pkg, travelTime, remainingDeliveryTime);

                        SwingUtilities.invokeLater(() -> {
                            MasterAgent.gui.addMessage(getLocalName() + " accepted rescue mission - will start after current delivery completes");
                            MasterAgent.gui.updateAgentStatus(getLocalName(), "Delivering (Rescue Queued)");
                        });
                        return;
                    }

                    // Stop any active timer (return journey timer)
                    if (currentTimer != null) {
                        currentTimer.stop();
                        currentTimer = null;
                    }

                    // If we're returning, interrupt the journey
                    if (agentState.equals("RETURNING")) {
                        currentTimeToMaster = 0;
                        consecutiveDeliveries = 0;

                        SwingUtilities.invokeLater(() -> {
                            MasterAgent.gui.addMessage(getLocalName() + " interrupted return journey - diverting to rescue " + failedAgent);
                            MasterAgent.gui.updateAgentTimes(getLocalName(), 0, 0);
                            MasterAgent.gui.updateConsecutiveDeliveries(getLocalName(), 0);
                        });
                    }

                    // If we're idle, also reset consecutive deliveries (altruistic rescue)
                    if (agentState.equals("IDLE")) {
                        consecutiveDeliveries = 0;
                        SwingUtilities.invokeLater(() -> {
                            MasterAgent.gui.updateConsecutiveDeliveries(getLocalName(), 0);
                        });
                    }

                    currentPackage = pkg;
                    agentState = "RESCUE";

                    SwingUtilities.invokeLater(() -> {
                        MasterAgent.gui.addMessage(getLocalName() + " awarded rescue mission for " + failedAgent);
                        MasterAgent.gui.updateAgentPackage(getLocalName(), pkg + " (RESCUE)");
                        MasterAgent.gui.updateAgentStatus(getLocalName(), "Rescue Mission");
                    });

                    startRescueMission(failedAgent, pkg, travelTime, remainingDeliveryTime);
                } else {
                    block();
                }
            }
        });

        // Listen for manual failure trigger
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.and(
                        MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                        MessageTemplate.MatchContent("TRIGGER_FAILURE")
                );
                ACLMessage msg = receive(mt);

                if (msg != null) {
                    // Only trigger if currently delivering
                    if (agentState.equals("DELIVERING")) {
                        triggerManualFailure();
                    }
                } else {
                    block();
                }
            }
        });

        // Listen for rescue completion notification (when being rescued)
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.and(
                        MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                        MessageTemplate.MatchContent("RESCUED")
                );
                ACLMessage msg = receive(mt);

                if (msg != null && isWaitingForRescue) {
                    String rescuerName = msg.getSender().getLocalName();
                    isWaitingForRescue = false;

                    SwingUtilities.invokeLater(() -> {
                        MasterAgent.gui.addMessage(getLocalName() + " has been rescued by " + rescuerName + ", returning to master");
                        MasterAgent.gui.updateAgentStatus(getLocalName(), "Rescued - Returning");
                    });

                    // Return to master after being rescued
                    returnToMasterAfterRescue();
                } else {
                    block();
                }
            }
        });
    }

    private void handleDistressCall(ACLMessage distressMsg) {
        String content = distressMsg.getContent();
        String[] parts = content.split(":");
        // DISTRESS_CALL:AgentName:Package:Distance:RemainingTime
        String strandedAgent = parts[1];
        String packageName = parts[2];
        int distanceFromMaster = Integer.parseInt(parts[3]);
        int remainingDeliveryTime = Integer.parseInt(parts[4]);

        // Don't respond to our own distress calls
        if (strandedAgent.equals(getLocalName())) {
            return;
        }

        // Calculate bid based on current state
        int bidTime = calculateRescueBid(distanceFromMaster);

        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.addMessage(getLocalName() + " received distress from " + strandedAgent + ", bidding: " + bidTime + "s");
        });

        // Send bid to master
        ACLMessage bid = new ACLMessage(ACLMessage.PROPOSE);
        bid.setContent("RESCUE_BID:" + strandedAgent + ":" + packageName + ":" + bidTime + ":" + getLocalName());
        bid.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
        bid.setConversationId(distressMsg.getConversationId());
        send(bid);
    }

    private int calculateRescueBid(int distanceToStranded) {
        int bidTime = 0;

        if (agentState.equals("IDLE") || agentState.equals("WAITING")) {
            // At master, bid is just travel time to stranded location
            bidTime = distanceToStranded;
        } else if (agentState.equals("DELIVERING")) {
            // Currently delivering - calculate when we'll be available
            // Time until we finish delivery + return to master + travel to stranded location
            int timeUntilAvailable = currentDeliveryTimeRemaining + savedDeliveryTime;
            bidTime = timeUntilAvailable + distanceToStranded;
        } else if (agentState.equals("RETURNING")) {
            // Returning to master, calculate direct travel from current position
            // I'm at distance currentTimeToMaster from master
            int myDistance = currentTimeToMaster;
            // Direct travel from my current position to stranded agent
            int directTravel = (int) (Math.random() * Math.max(myDistance, distanceToStranded));
            bidTime = directTravel;
        } else {
            // Other states (RESCUE, WAITING_RESCUE, etc.) - make bid high but realistic
            bidTime = 9999;
        }

        // Add random factor (0-3 seconds) to simulate uncertainty/distance variation
        bidTime += (int) (Math.random() * 4);

        // Add penalty for consecutive deliveries (fatigue)
        bidTime += consecutiveDeliveries * 2;

        return bidTime;
    }

    private void startCooldown() {
        Timer cooldownTimer = new Timer(5000, e -> {
            consecutiveDeliveries = 0;
            SwingUtilities.invokeLater(() -> {
                MasterAgent.gui.addMessage(getLocalName() + " cooldown complete, ready for work");
                MasterAgent.gui.updateAgentStatus(getLocalName(), "Idle (At Master)");
                MasterAgent.gui.updateConsecutiveDeliveries(getLocalName(), 0);
            });

            // Request next package
            ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
            request.setContent("REQUEST_PACKAGE");
            request.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
            send(request);

            ((Timer)e.getSource()).stop();
        });
        cooldownTimer.start();
    }

    private void startRescueMission(String failedAgent, String pkg, int travelTime, int remainingDeliveryTime) {
        Timer travelTimer = new Timer(1000, null);
        final int[] tt = {travelTime};
        savedDeliveryTime = remainingDeliveryTime;

        travelTimer.addActionListener(e -> {
            if (MasterAgent.gui.isSystemPaused()) {
                return;
            }

            if (tt[0] > 0) {
                tt[0]--;
                final int remaining = tt[0];
                SwingUtilities.invokeLater(() -> {
                    MasterAgent.gui.updateAgentTimes(getLocalName(), remaining, 0);
                });
            } else if (tt[0] == 0) {
                travelTimer.stop();

                // CRITICAL FIX: Verify we're still in RESCUE state
                if (!agentState.equals("RESCUE")) {
                    return;
                }

                SwingUtilities.invokeLater(() -> {
                    MasterAgent.gui.addMessage(getLocalName() + " reached " + failedAgent + ", taking package " + pkg);
                });

                // Notify failed agent that they've been rescued
                ACLMessage rescuedMsg = new ACLMessage(ACLMessage.INFORM);
                rescuedMsg.setContent("RESCUED");
                rescuedMsg.addReceiver(new AID(failedAgent, AID.ISLOCALNAME));
                send(rescuedMsg);

                // Notify master about rescue completion
                ACLMessage rescueCompleteMsg = new ACLMessage(ACLMessage.INFORM);
                rescueCompleteMsg.setContent("RESCUE_COMPLETE:" + failedAgent + ":" + pkg);
                rescueCompleteMsg.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
                send(rescueCompleteMsg);

                // Start delivery with remaining time
                startDelivery(pkg, remainingDeliveryTime);
            }
        });
        currentTimer = travelTimer;
        travelTimer.start();
    }

    private void startDelivery(String pkg, int deliveryTime) {
        Timer timer = new Timer(1000, null);
        final int[] dt = {deliveryTime};
        savedDeliveryTime = deliveryTime;
        currentDeliveryTimeRemaining = deliveryTime;
        currentDistanceFromMaster = 0;
        agentState = "DELIVERING";

        timer.addActionListener(e -> {
            if (MasterAgent.gui.isSystemPaused()) {
                return;
            }

            if (dt[0] > 0) {
                // Delivering phase
                dt[0]--;
                currentDeliveryTimeRemaining = dt[0];
                currentDistanceFromMaster = savedDeliveryTime - dt[0];

                SwingUtilities.invokeLater(() -> {
                    MasterAgent.gui.updateAgentTimes(getLocalName(), 0, dt[0]);
                });
            } else {
                // Delivery complete
                timer.stop();
                currentTimer = null;

                // CRITICAL FIX: Check if agent was reassigned during delivery
                // If agent is in RESCUE state, it means they were interrupted
                // Don't proceed with normal return - the rescue handler will take over
                if (!agentState.equals("DELIVERING")) {
                    return;
                }

                consecutiveDeliveries++;
                currentTimeToMaster = savedDeliveryTime;
                agentState = "RETURNING";

                SwingUtilities.invokeLater(() -> {
                    MasterAgent.gui.addMessage(getLocalName() + " finished delivery of " + pkg + ", returning to master (" + currentTimeToMaster + "s)");
                    MasterAgent.gui.updateAgentTimes(getLocalName(), currentTimeToMaster, 0);
                    MasterAgent.gui.updateConsecutiveDeliveries(getLocalName(), consecutiveDeliveries);
                });

                // Notify master of completion
                ACLMessage completeMsg = new ACLMessage(ACLMessage.INFORM);
                completeMsg.setContent("DELIVERY_COMPLETE:" + pkg);
                completeMsg.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
                send(completeMsg);

                // Check if there's a pending rescue mission
                if (pendingRescue != null) {
                    PendingRescueMission rescue = pendingRescue;
                    pendingRescue = null;

                    // Start rescue mission immediately instead of returning
                    currentTimeToMaster = 0;
                    consecutiveDeliveries = 0;
                    currentPackage = rescue.pkg;
                    agentState = "RESCUE";

                    SwingUtilities.invokeLater(() -> {
                        MasterAgent.gui.addMessage(getLocalName() + " starting pending rescue mission for " + rescue.failedAgent);
                        MasterAgent.gui.updateAgentPackage(getLocalName(), rescue.pkg + " (RESCUE)");
                        MasterAgent.gui.updateAgentStatus(getLocalName(), "Rescue Mission");
                        MasterAgent.gui.updateAgentTimes(getLocalName(), 0, 0);
                    });

                    startRescueMission(rescue.failedAgent, rescue.pkg, rescue.travelTime, rescue.remainingDeliveryTime);
                } else {
                    // Start normal return journey
                    startReturnToMaster();
                }
            }
        });
        currentTimer = timer;
        timer.start();
    }

    private void failAtLocation(int totalDeliveryTime, int remainingTime) {
        agentState = "WAITING_RESCUE";
        isWaitingForRescue = true;
        int timeTraveled = totalDeliveryTime - remainingTime;
        String failedPackage = currentPackage;

        if (currentTimer != null) {
            currentTimer.stop();
            currentTimer = null;
        }

        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.addMessage("⚠️ CRITICAL FAILURE! " + getLocalName() + " failed at location with " + failedPackage);
            MasterAgent.gui.addMessage("   → Agent stranded " + timeTraveled + "s from master, " + remainingTime + "s of delivery remaining");
            MasterAgent.gui.updateAgentStatus(getLocalName(), "STRANDED - Broadcasting SOS");
        });

        // Broadcast distress call to ALL agents (peer-to-peer)
        ACLMessage distress = new ACLMessage(ACLMessage.CFP); // Call For Proposals
        distress.setContent("DISTRESS_CALL:" + getLocalName() + ":" + failedPackage + ":" + timeTraveled + ":" + remainingTime);
        distress.setConversationId("RESCUE-" + getLocalName() + "-" + System.currentTimeMillis());

        // Broadcast to all agents
        distress.addReceiver(new AID("Agent1", AID.ISLOCALNAME));
        distress.addReceiver(new AID("Agent2", AID.ISLOCALNAME));
        distress.addReceiver(new AID("Agent3", AID.ISLOCALNAME));

        // Also inform master
        distress.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));

        send(distress);

        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.addMessage(getLocalName() + " broadcasting distress call to all agents...");
            MasterAgent.gui.updateAgentStatus(getLocalName(), "STRANDED - Awaiting Bids");
        });
    }

    private void returnToMasterAfterRescue() {
        // Calculate return time based on where we were stranded
        int returnTime = savedDeliveryTime - currentDeliveryTimeRemaining;
        currentTimeToMaster = returnTime;
        agentState = "RETURNING";
        currentPackage = null;

        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.updateAgentTimes(getLocalName(), returnTime, 0);
            MasterAgent.gui.updateAgentPackage(getLocalName(), null);
        });

        Timer returnTimer = new Timer(1000, null);
        final int[] ttm = {currentTimeToMaster};

        returnTimer.addActionListener(e -> {
            if (MasterAgent.gui.isSystemPaused()) {
                return;
            }

            if (ttm[0] > 0) {
                ttm[0]--;
                currentTimeToMaster = ttm[0];
                SwingUtilities.invokeLater(() -> {
                    MasterAgent.gui.updateAgentTimes(getLocalName(), ttm[0], 0);
                });
            } else {
                returnTimer.stop();
                currentTimeToMaster = 0;
                agentState = "IDLE";
                consecutiveDeliveries = 0; // Reset after rescue

                SwingUtilities.invokeLater(() -> {
                    MasterAgent.gui.addMessage(getLocalName() + " arrived at master after being rescued");
                    MasterAgent.gui.updateAgentTimes(getLocalName(), 0, 0);
                    MasterAgent.gui.updateAgentStatus(getLocalName(), "Idle (At Master)");
                    MasterAgent.gui.updateConsecutiveDeliveries(getLocalName(), 0);
                });

                // Request next package
                ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                request.setContent("REQUEST_PACKAGE");
                request.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
                send(request);
            }
        });
        returnTimer.start();
    }

    private void startReturnToMaster() {
        Timer returnTimer = new Timer(1000, null);
        final int[] ttm = {currentTimeToMaster};

        returnTimer.addActionListener(e -> {
            if (MasterAgent.gui.isSystemPaused()) {
                return;
            }

            if (ttm[0] > 0) {
                ttm[0]--;
                currentTimeToMaster = ttm[0];
                SwingUtilities.invokeLater(() -> {
                    MasterAgent.gui.updateAgentTimes(getLocalName(), ttm[0], 0);
                });
            } else {
                // Back at master
                returnTimer.stop();
                currentTimer = null;
                currentTimeToMaster = 0;
                String pkgName = currentPackage;
                currentPackage = null;
                agentState = "IDLE";

                SwingUtilities.invokeLater(() -> {
                    MasterAgent.gui.addMessage(getLocalName() + " arrived at master (delivered " + pkgName + "), requesting next package");
                    MasterAgent.gui.updateAgentTimes(getLocalName(), 0, 0);
                    MasterAgent.gui.updateAgentPackage(getLocalName(), null);
                    MasterAgent.gui.updateConsecutiveDeliveries(getLocalName(), consecutiveDeliveries);
                });

                // Request next package from master
                ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                request.setContent("REQUEST_PACKAGE");
                request.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
                send(request);
            }
        });
        currentTimer = returnTimer;
        returnTimer.start();
    }

    // Manual failure trigger
    public void triggerManualFailure() {
        if (agentState.equals("DELIVERING") && currentTimer != null) {
            currentTimer.stop();
            failAtLocation(savedDeliveryTime, currentDeliveryTimeRemaining);
        }
    }

    public String getAssignedPackageType() {
        return assignedPackageType;
    }
}