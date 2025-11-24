package testCase_8;

import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Timer;
import java.util.TimerTask;

public class DeliveryAgent extends Agent {
    private int capacity;
    private int currentLoad = 0;
    private int maxItems = 3;
    private int currentItems = 0;
    private boolean hasSentRoundComplete = false;
    private Timer deliveryTimer;
    private Timer returnTimer;

    private List<PackageInTransit> packagesCarrying = new ArrayList<>();
    private boolean isAtMaster = true;
    private boolean started = false;
    private boolean paused = false;
    private int currentRound = 0;

    private double speedFactor;
    private String speedType;

    private List<ACLMessage> receivedProposals = new ArrayList<>();
    private boolean waitingForNegotiation = false;

    private int totalItemsDelivered = 0;
    private int totalDistanceTraveled = 0;

    @Override
    protected void setup() {
        String name = getLocalName();

        capacity = switch (name) {
            case "Agent1" -> 15;
            case "Agent2" -> 18;
            case "Agent3" -> 20;
            default -> 15;
        };

        speedFactor = switch (name) {
            case "Agent1" -> 0.7;
            case "Agent2" -> 1.0;
            case "Agent3" -> 1.4;
            default -> 1.0;
        };

        speedType = switch ((int)(speedFactor * 10)) {
            case 7  -> "Fast";
            case 14 -> "Slow";
            default -> "Normal";
        };

        ACLMessage readyMsg = new ACLMessage(ACLMessage.SUBSCRIBE);
        readyMsg.setContent("READY");
        readyMsg.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
        send(readyMsg);

        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    if (msg.getPerformative() == ACLMessage.INFORM) {
                        handleInformMessage(msg);
                    } else if (msg.getPerformative() == ACLMessage.PROPOSE) {
                        // Check if sender is Master (for package offer) or other agent (for negotiation)
                        if (msg.getSender().getLocalName().equals("MasterAgent")) {
                            handlePackageOffer(msg);
                        } else if (waitingForNegotiation) {
                            receivedProposals.add(msg);
                        } // Ignore if not waiting and not from Master
                    } else if (msg.getPerformative() == ACLMessage.REFUSE) {
                        if (waitingForNegotiation) {
                            // Just log refusal, continue waiting for other proposals
                        } else {
                            handleRefusal(msg);
                        }
                    } else if (msg.getPerformative() == ACLMessage.CFP) {
                        handleNegotiationCFP(msg);
                    } else if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                        handleNegotiationAccept(msg);
                    } else if (msg.getPerformative() == ACLMessage.PROPOSE) {
                        if (waitingForNegotiation) {
                            receivedProposals.add(msg);
                        } else {
                            handlePackageOffer(msg);
                        }
                    } else if (msg.getPerformative() == ACLMessage.REFUSE) {
                        if (waitingForNegotiation) {
                            // Just log refusal, continue waiting for other proposals
                        } else {
                            handleRefusal(msg);
                        }
                    }
                } else {
                    block();
                }
            }
        });
    }

    private void handleInformMessage(ACLMessage msg) {
        String content = msg.getContent();

        if ("PAUSE".equals(content)) {
            paused = true;
        } else if ("RESUME".equals(content)) {
            paused = false;
            if (isAtMaster) {
                doWait(1000);
                requestPackage(null);
            }
        } else if (content.startsWith("START:ROUND:")) {
            hasSentRoundComplete = false;
            currentRound = Integer.parseInt(content.split(":")[2]);
            if (!started) {
                started = true;
                SwingUtilities.invokeLater(() -> {
                    MasterAgent.gui.updateAgentSpeed(getLocalName(), speedType, speedFactor);
                    MasterAgent.gui.updateAgentCapacity(getLocalName(), capacity);
                    MasterAgent.gui.addMessage("🚀 " + getLocalName() + " starting Round " + currentRound +
                            " | Capacity: " + capacity + "kg, " + maxItems + " items");
                });
            }
            requestPackage(null);
        }
    }

    private void requestPackage(String specificPkgName) {
        if (!isAtMaster || paused) return;

        ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
        String content = specificPkgName != null ? "REQUEST_SPECIFIC:" + specificPkgName + ":" : "REQUEST:";
        content += currentLoad + ":" + capacity + ":" + currentItems + ":" + maxItems;
        req.setContent(content);
        req.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
        send(req);
    }

    private void handlePackageOffer(ACLMessage msg) {
        String[] p = msg.getContent().split(":");
        String pkgName = p[0];
        int baseTime = Integer.parseInt(p[1]);
        int weight = Integer.parseInt(p[2]);

        boolean fitsWeight = (currentLoad + weight <= capacity);
        boolean fitsItemCount = (currentItems + 1 <= maxItems);

        if (!fitsWeight || !fitsItemCount) {
            String reason = !fitsWeight ? "weight limit" : "item limit";
            SwingUtilities.invokeLater(() -> {
                MasterAgent.gui.addMessage("❌ REJECT → " + getLocalName() + " cannot accept " + pkgName +
                        " (" + reason + " reached: " + currentLoad + "/" + capacity +
                        "kg, " + currentItems + "/" + maxItems + " items)");
            });

            ACLMessage refuse = new ACLMessage(ACLMessage.REFUSE);
            refuse.setContent("REJECT_OFFER:" + pkgName);
            refuse.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
            send(refuse);

            startNegotiation(pkgName, weight, baseTime);
            return;
        }

        currentLoad += weight;
        currentItems++;
        packagesCarrying.add(new PackageInTransit(pkgName, baseTime, weight, speedFactor));

        ACLMessage confirm = new ACLMessage(ACLMessage.CONFIRM);
        confirm.setContent(pkgName);
        confirm.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
        send(confirm);

        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.updateAgentLoad(getLocalName(), currentLoad, capacity);
            MasterAgent.gui.updateAgentPackages(getLocalName(), getPackageNames() +
                    " [" + currentItems + "/" + maxItems + " items]");
        });

        if (currentLoad < capacity && currentItems < maxItems) {
            doWait(500);
            if (!paused) requestPackage(null);
        } else {
            SwingUtilities.invokeLater(() -> {
                String reason = (currentItems >= maxItems) ? "Item limit reached" : "Weight limit reached";
                MasterAgent.gui.addMessage("🚚 FULL → " + getLocalName() + " (" + reason +
                        ") | Starting delivery | " + currentItems + " items, " +
                        currentLoad + "kg");
            });
            startDelivery();
        }
    }

    private void handleRefusal(ACLMessage msg) {
        String reason = msg.getContent();

        if ("NO_PACKAGES".equals(reason)) {
            if (packagesCarrying.isEmpty() && currentItems == 0 && isAtMaster && !hasSentRoundComplete) {
                hasSentRoundComplete = true;
                SwingUtilities.invokeLater(() -> {
                    MasterAgent.gui.addMessage("✅ " + getLocalName() + " | Round " + currentRound +
                            " complete | Delivered: " + totalItemsDelivered +
                            " items, " + totalDistanceTraveled + "s");
                });

                ACLMessage roundComplete = new ACLMessage(ACLMessage.INFORM);
                roundComplete.setContent("ROUND_COMPLETE:" + currentRound);
                roundComplete.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
                send(roundComplete);
            }

            doWait(3000);
            if (!paused) requestPackage(null);

        } else if ("CAPACITY_FULL".equals(reason)) {
            if (packagesCarrying.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    MasterAgent.gui.addMessage("🤝 TRIGGER NEGOTIATION → " + getLocalName() +
                            " | Cannot carry ANY available package!");
                });

                doWait(3000);
                if (!paused) requestPackage(null);

            } else {
                SwingUtilities.invokeLater(() -> {
                    MasterAgent.gui.addMessage("🚚 START DELIVERY → " + getLocalName() +
                            " | Full capacity, delivering current load");
                });
                startDelivery();
            }
        }
    }

    private void startNegotiation(String packageName, int weight, int travelTime) {
        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.addMessage("🤝 NEGOTIATION → " + getLocalName() +
                    " broadcasts CFP for " + packageName + " (" + weight + "kg, " +
                    travelTime + "s)");
        });

        for (int i = 1; i <= 3; i++) {
            String otherAgent = "Agent" + i;
            if (!otherAgent.equals(getLocalName())) {
                ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                cfp.setContent("HELP:" + packageName + ":" + weight + ":" + travelTime + ":from:" + getLocalName());
                cfp.addReceiver(new AID(otherAgent, AID.ISLOCALNAME));
                cfp.setReplyWith(packageName + System.currentTimeMillis());
                send(cfp);
            }
        }

        waitingForNegotiation = true;
        receivedProposals.clear();

        addBehaviour(new jade.core.behaviours.WakerBehaviour(this, 3000) {
            @Override
            protected void onWake() {
                evaluateProposals(packageName);
            }
        });
    }

    private void handleNegotiationCFP(ACLMessage msg) {
        String content = msg.getContent();
        String[] parts = content.split(":");

        if (parts[0].equals("HELP")) {
            String packageName = parts[1];
            int weight = Integer.parseInt(parts[2]);
            int travelTime = Integer.parseInt(parts[3]);
            String requestingAgent = parts[5];

            boolean canHelpWeight = (currentLoad + weight <= capacity);
            boolean canHelpItems = (currentItems + 1 <= maxItems);
            boolean canHelp = canHelpWeight && canHelpItems && isAtMaster;

            if (canHelp) {
                int availableCapacity = capacity - currentLoad;
                int availableItems = maxItems - currentItems;
                int score = (availableCapacity * 10) + (availableItems * 20);

                ACLMessage proposal = new ACLMessage(ACLMessage.PROPOSE);
                proposal.setContent("CAN_HELP:" + packageName + ":score:" + score +
                        ":capacity:" + availableCapacity + ":items:" + availableItems);
                proposal.addReceiver(new AID(requestingAgent, AID.ISLOCALNAME));
                proposal.setInReplyTo(msg.getReplyWith());
                send(proposal);

                SwingUtilities.invokeLater(() -> {
                    MasterAgent.gui.addMessage("   💬 PROPOSE → " + getLocalName() + " to " +
                            requestingAgent + " | Score: " + score +
                            " (cap: " + availableCapacity + "kg, items: " +
                            availableItems + ")");
                });
            } else {
                ACLMessage refusal = new ACLMessage(ACLMessage.REFUSE);
                String reason = !canHelpWeight ? "weight full" :
                        !canHelpItems ? "items full" : "not at master";
                refusal.setContent("CANNOT_HELP:" + packageName + ":reason:" + reason);
                refusal.addReceiver(new AID(requestingAgent, AID.ISLOCALNAME));
                refusal.setInReplyTo(msg.getReplyWith());
                send(refusal);

                SwingUtilities.invokeLater(() -> {
                    MasterAgent.gui.addMessage("   ❌ REFUSE → " + getLocalName() + " to " +
                            requestingAgent + " (" + reason + ")");
                });
            }
        }
    }

    private void handleNegotiationAccept(ACLMessage msg) {
        String content = msg.getContent();
        String[] parts = content.split(":");
        String packageName = parts[1];

        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.addMessage("✅ ACCEPTED → " + getLocalName() +
                    " wins negotiation for " + packageName +
                    " from " + msg.getSender().getLocalName());
        });

        doWait(500);
        requestPackage(packageName); // Request specific package
    }

    private void evaluateProposals(String packageName) {
        waitingForNegotiation = false;

        if (receivedProposals.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                MasterAgent.gui.addMessage("   ⚠️ NO HELP → " + getLocalName() +
                        " | No agents available for " + packageName);
            });

            if (!packagesCarrying.isEmpty()) {
                startDelivery();
            } else {
                doWait(2000);
                if (!paused) requestPackage(null);
            }
            return;
        }

        ACLMessage bestProposal = null;
        int bestScore = -1;

        for (ACLMessage proposal : receivedProposals) {
            String content = proposal.getContent();
            String[] parts = content.split(":");
            int score = Integer.parseInt(parts[3]);

            if (score > bestScore) {
                bestScore = score;
                bestProposal = proposal;
            }
        }

        if (bestProposal != null) {
            String winnerAgent = bestProposal.getSender().getLocalName();
            int finalBestScore = bestScore;

            SwingUtilities.invokeLater(() -> {
                MasterAgent.gui.addMessage("   🏆 WINNER → " + winnerAgent +
                        " selected (score: " + finalBestScore + ")");
            });

            ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
            accept.setContent("ACCEPTED:" + packageName);
            accept.addReceiver(bestProposal.getSender());
            send(accept);

            for (ACLMessage proposal : receivedProposals) {
                if (!proposal.getSender().equals(bestProposal.getSender())) {
                    ACLMessage reject = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                    reject.setContent("REJECTED:" + packageName);
                    reject.addReceiver(proposal.getSender());
                    send(reject);
                }
            }
        }

        receivedProposals.clear();

        if (!packagesCarrying.isEmpty()) {
            startDelivery();
        } else {
            doWait(1000);
            if (!paused) requestPackage(null);
        }
    }

    private void startDelivery() {
        isAtMaster = false;
        packagesCarrying.sort(Comparator.comparingInt(p -> p.actualTravelTime));
        SwingUtilities.invokeLater(() -> MasterAgent.gui.updateAgentStatus(getLocalName(), "DELIVERING"));
        deliverNextPackage();
    }

    private void deliverNextPackage() {
        if (packagesCarrying.isEmpty()) {
            returnToMaster();
            return;
        }

        PackageInTransit pkg = packagesCarrying.get(0);

        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.addMessage("🚚 DELIVERING → " + getLocalName() + " | " + pkg.name +
                    " (base " + pkg.baseTravelTime + "s → " + pkg.actualTravelTime + "s | " +
                    speedType + ") | Items: " + currentItems + "/" + maxItems);
            MasterAgent.gui.updateAgentTravelTime(getLocalName(), pkg.actualTravelTime);
        });

        if (deliveryTimer != null) {
            deliveryTimer.cancel();
        }
        deliveryTimer = new Timer(true); // Daemon timer to auto-terminate

        int[] left = {pkg.actualTravelTime};

        deliveryTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                left[0]--;
                SwingUtilities.invokeLater(() -> MasterAgent.gui.updateAgentTravelTime(getLocalName(), left[0]));
                if (left[0] <= 0) {
                    deliveryTimer.cancel();
                    completeDelivery(pkg);
                }
            }
        }, 1000, 1000);
    }

    private void completeDelivery(PackageInTransit pkg) {
        currentLoad -= pkg.weight;
        currentItems--;
        packagesCarrying.remove(pkg);

        totalItemsDelivered++;
        totalDistanceTraveled += pkg.actualTravelTime;

        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.updateAgentLoad(getLocalName(), currentLoad, capacity);
            MasterAgent.gui.updateAgentPackages(getLocalName(), getPackageNames() +
                    " [" + currentItems + "/" + maxItems + " items]");
        });

        ACLMessage m = new ACLMessage(ACLMessage.INFORM);
        m.setContent("DELIVERED:" + pkg.name + ":" + pkg.actualTravelTime);
        m.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
        send(m);

        doWait(500);
        deliverNextPackage();
    }

    private void returnToMaster() {
        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.addMessage("🔙 RETURNING → " + getLocalName() + " | Delivered " +
                    currentItems + " items this trip");
            MasterAgent.gui.updateAgentStatus(getLocalName(), "RETURNING");
        });

        if (returnTimer != null) {
            returnTimer.cancel();
        }
        returnTimer = new Timer(true); // Daemon timer

        int[] left = {3};

        returnTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                left[0]--;
                if (left[0] <= 0) {
                    returnTimer.cancel();
                    arriveAtMaster();
                }
            }
        }, 1000, 1000);
    }

    private void arriveAtMaster() {
        isAtMaster = true;
        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.updateAgentStatus(getLocalName(), "AT_MASTER");
            MasterAgent.gui.updateAgentTravelTime(getLocalName(), 0);
        });
        doWait(1000);
        if (!paused) requestPackage(null);
    }

    private String getPackageNames() {
        return packagesCarrying.isEmpty() ? "None" :
                packagesCarrying.stream().map(p -> p.name).collect(Collectors.joining(", "));
    }

    @Override
    protected void takeDown() {
        if (deliveryTimer != null) {
            deliveryTimer.cancel();
        }
        if (returnTimer != null) {
            returnTimer.cancel();
        }
        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.addMessage("Agent " + getLocalName() + " terminated safely.");
        });
        super.takeDown();
    }

    static class PackageInTransit {
        String name;
        int baseTravelTime;
        int actualTravelTime;
        int weight;

        PackageInTransit(String n, int b, int w, double f) {
            name = n;
            baseTravelTime = b;
            weight = w;
            actualTravelTime = Math.max(1, (int)Math.round(b * f));
        }
    }
}