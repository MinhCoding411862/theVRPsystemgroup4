package testCase_9;

import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.*;

public class MasterAgent extends Agent {
    public static DeliveryGUI gui;
    private Map<String, PackageInfo> availablePackages = new LinkedHashMap<>();
    private Map<String, PackageInfo> offeredPackages = new HashMap<>();
    private int agentCount = 3;
    private Set<String> readyAgents = new HashSet<>();
    private Set<String> roundCompletedAgents = new HashSet<>();
    private boolean systemPaused = false;

    private int totalItemsDelivered = 0;
    private int totalDistanceTraveled = 0;
    private int currentRound = 1;

    @Override
    protected void setup() {
        gui = new DeliveryGUI();
        gui.setOnPauseCallback(this::pauseSystem);
        gui.setOnResumeCallback(this::resumeSystem);
        gui.setOnPriorityCallback(this::createPriorityPackage);

        gui.addMessage("=========================================");
        gui.addMessage("MASTER AGENT INITIALIZED");
        gui.addMessage("=========================================");
        gui.addMessage("System Mode: NEGOTIATION-BASED DELIVERY");
        gui.addMessage("   -> Priority: Items Delivered > Distance");
        gui.addMessage("   -> Agents negotiate package allocation");
        gui.addMessage("   -> Dual constraints: Weight + Item count");
        gui.addMessage("=========================================");
        gui.addMessage("");

        createPackage("P1", 3, 10);
        createPackage("P2", 5, 9);
        createPackage("P3", 2, 8);
        createPackage("P4", 4, 12);
        createPackage("P5", 3, 11);
        createPackage("P6", 6, 10);
        createPackage("P7", 2, 4);
        createPackage("P8", 4, 5);
        createPackage("P9", 5, 3);
        createPackage("P10", 3, 6);
        createPackage("P11", 4, 7);
        createPackage("P12", 2, 4);

        gui.addMessage("PACKAGES DESIGNED FOR NEGOTIATION:");
        gui.addMessage("   -> Heavy packages (9-12kg): Will cause conflicts");
        gui.addMessage("   -> Light packages (3-6kg): For negotiation resolution");
        gui.addMessage("   -> Total: 12 packages | Agents must negotiate!");
        gui.addMessage("");

        for (int i = 1; i <= agentCount; i++) {
            String agentName = "Agent" + i;
            int capacity = switch (i) {
                case 1 -> 15;
                case 2 -> 18;
                case 3 -> 20;
                default -> 15;
            };
            gui.addAgent(agentName, capacity);
        }

        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) {
                    block();
                    return;
                }

                if (msg.getPerformative() == ACLMessage.SUBSCRIBE) {
                    handleAgentReady(msg);
                } else if (msg.getPerformative() == ACLMessage.REQUEST) {
                    handlePackageRequest(msg);
                } else if (msg.getPerformative() == ACLMessage.CONFIRM) {
                    handlePackagePickup(msg);
                } else if (msg.getPerformative() == ACLMessage.INFORM && msg.getContent().startsWith("DELIVERED:")) {
                    handleDeliveryComplete(msg);
                } else if (msg.getPerformative() == ACLMessage.CFP) {
                    handleNegotiationRequest(msg);
                } else if (msg.getPerformative() == ACLMessage.INFORM && msg.getContent().startsWith("ROUND_COMPLETE:")) {
                    handleRoundComplete(msg);
                } else if (msg.getPerformative() == ACLMessage.REFUSE && msg.getContent().startsWith("REJECT_OFFER:")) {
                    handleOfferRejection(msg);
                }
            }
        });
    }

    private void handleAgentReady(ACLMessage msg) {
        String agentName = msg.getSender().getLocalName();
        readyAgents.add(agentName);
        gui.addMessage(agentName + " is READY");

        if (readyAgents.size() == agentCount) {
            gui.addMessage("");
            gui.addMessage("========================================");
            gui.addMessage("ALL AGENTS READY - STARTING ROUND 1");
            gui.addMessage("========================================");
            gui.addMessage("");

            for (String agent : readyAgents) {
                ACLMessage startMsg = new ACLMessage(ACLMessage.INFORM);
                startMsg.setContent("START:ROUND:" + currentRound);
                startMsg.addReceiver(new AID(agent, AID.ISLOCALNAME));
                send(startMsg);
            }
        }
    }

    private void handlePackageRequest(ACLMessage msg) {
        if (systemPaused) return;

        String agentName = msg.getSender().getLocalName();
        String content = msg.getContent();

        String[] parts = content.split(":");
        boolean isSpecific = parts[0].equals("REQUEST_SPECIFIC");
        String specificPkgName = isSpecific ? parts[1] : null;
        int offset = isSpecific ? 2 : 1;
        int currentLoad = Integer.parseInt(parts[offset]);
        int capacity = Integer.parseInt(parts[offset + 1]);
        int currentItems = Integer.parseInt(parts[offset + 2]);
        int maxItems = Integer.parseInt(parts[offset + 3]);

        ACLMessage reply = msg.createReply();

        synchronized (availablePackages) {
            PackageInfo selectedInfo = null;
            if (isSpecific) {
                selectedInfo = availablePackages.get(specificPkgName);
                if (selectedInfo != null) {
                    boolean fitsWeight = (currentLoad + selectedInfo.weight <= capacity);
                    boolean fitsItemCount = (currentItems + 1 <= maxItems);
                    if (!fitsWeight || !fitsItemCount) {
                        selectedInfo = null;
                    }
                }
            } else {
                selectedInfo = findBestPackage(currentLoad, capacity, currentItems, maxItems);
            }

            if (selectedInfo != null) {
                reply.setPerformative(ACLMessage.PROPOSE);
                reply.setContent(selectedInfo.name + ":" + selectedInfo.travelTime + ":" + selectedInfo.weight);
                send(reply);

                offeredPackages.put(selectedInfo.name, selectedInfo);

                gui.addMessage("OFFER -> " + agentName + " | " + selectedInfo.name +
                        " (" + selectedInfo.weight + "kg, " + selectedInfo.travelTime + "s) | Items: " +
                        (currentItems + 1) + "/" + maxItems +
                        " -> Will NEGOTIATE if not taken");
            } else {
                reply.setPerformative(ACLMessage.REFUSE);
                reply.setContent("NO_PACKAGES");
                send(reply);
                gui.addMessage("NO PACKAGES -> " + agentName + " | Waiting...");
            }
        }
    }

    private PackageInfo findBestPackage(int currentLoad, int capacity, int currentItems, int maxItems) {
        return availablePackages.values().stream()
                .filter(pkg -> (currentLoad + pkg.weight <= capacity) && (currentItems + 1 <= maxItems))
                .sorted(Comparator.comparing((PackageInfo p) -> p.priority ? 0 : 1))
                .findFirst()
                .orElse(availablePackages.values().stream()
                        .min(Comparator.comparingInt(p -> p.weight))
                        .orElse(null));
    }

    private void handlePackagePickup(ACLMessage msg) {
        String packageName = msg.getContent();
        String agentName = msg.getSender().getLocalName();

        synchronized (availablePackages) {
            PackageInfo pkg = offeredPackages.remove(packageName);
            if (pkg != null) {
                availablePackages.remove(packageName);
                gui.addMessage("PICKUP -> " + agentName + " | " + packageName);
                gui.addMessage("   -> Remaining packages: " + availablePackages.size());
            } else {
                gui.addMessage("ERROR: Package " + packageName + " not in offered list!");
            }
        }
    }

    private void handleOfferRejection(ACLMessage msg) {
        String content = msg.getContent();
        String[] parts = content.split(":");
        String packageName = parts[1];

        synchronized (availablePackages) {
            PackageInfo pkg = offeredPackages.remove(packageName);
            if (pkg != null) {
                availablePackages.put(packageName, pkg);
                gui.addMessage("OFFER REJECTED -> " + packageName + " returned to pool | Remaining: " + availablePackages.size());
            }
        }
    }

    private void handleDeliveryComplete(ACLMessage msg) {
        String content = msg.getContent();
        String[] parts = content.split(":");
        String packageName = parts[1];
        int distance = Integer.parseInt(parts[2]);
        String agentName = msg.getSender().getLocalName();

        totalItemsDelivered++;
        totalDistanceTraveled += distance;

        gui.addMessage("DELIVERED -> " + agentName + " | " + packageName + " (" + distance + "s)");
        gui.addMessage("   -> Total: " + totalItemsDelivered + " items, " + totalDistanceTraveled + "s distance");
        gui.incrementDeliveryCount();
        gui.updateOptimizationStats(totalItemsDelivered, totalDistanceTraveled);
        gui.removeMasterPackage(packageName);

        if (!systemPaused) {
            synchronized (availablePackages) {
                int pkgNum = (int) (Math.random() * 1000);
                String newPkgName = "P" + pkgNum;
                int travelTime = 2 + (int) (Math.random() * 5);
                int weight = 3 + (int) (Math.random() * 7);

                createPackage(newPkgName, travelTime, weight);
                gui.addMessage("   -> Created new package " + newPkgName + " | Total: " + availablePackages.size());
            }
        }
    }

    private void handleNegotiationRequest(ACLMessage msg) {
        String content = msg.getContent();
        String agentName = msg.getSender().getLocalName();

        String[] parts = content.split(":");
        String packageName = parts[1];
        int weight = Integer.parseInt(parts[2]);
        int travelTime = Integer.parseInt(parts[3]);

        gui.addMessage("NEGOTIATION -> " + agentName + " requests help with " + packageName +
                " (" + weight + "kg, " + travelTime + "s)");

        for (String otherAgent : readyAgents) {
            if (!otherAgent.equals(agentName)) {
                ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                cfp.setContent("HELP:" + packageName + ":" + weight + ":" + travelTime + ":from:" + agentName);
                cfp.addReceiver(new AID(otherAgent, AID.ISLOCALNAME));
                send(cfp);
            }
        }
    }

    private void handleRoundComplete(ACLMessage msg) {
        String agentName = msg.getSender().getLocalName();
        String content = msg.getContent();
        int reportedRound = Integer.parseInt(content.split(":")[1]);

        if (reportedRound != currentRound || roundCompletedAgents.contains(agentName)) {
            return;
        }
        roundCompletedAgents.add(agentName);
        gui.addMessage("ROUND COMPLETE -> " + agentName + " finished Round " + currentRound);

        if (roundCompletedAgents.size() == readyAgents.size()) {
            startNewRound();
        }
    }

    private void startNewRound() {
        currentRound++;
        roundCompletedAgents.clear();
        gui.addMessage("========================================");
        gui.addMessage("STARTING ROUND " + currentRound);
        gui.addMessage("   -> Packages remaining: " + availablePackages.size());
        gui.addMessage("========================================");

        for (String agent : readyAgents) {
            ACLMessage startMsg = new ACLMessage(ACLMessage.INFORM);
            startMsg.setContent("START:ROUND:" + currentRound);
            startMsg.addReceiver(new AID(agent, AID.ISLOCALNAME));
            send(startMsg);
        }
    }

    private void createPriorityPackage() {
        synchronized (availablePackages) {
            int pkgNum = (int) (System.currentTimeMillis() % 10000);
            String name = "P" + pkgNum + "-PRI";
            int travelTime = 2 + (int)(Math.random()*4);
            int weight = 4 + (int)(Math.random()*6);

            PackageInfo pkg = new PackageInfo(name, travelTime, weight, true);
            availablePackages.put(name, pkg);
            gui.addMasterPackage(name, travelTime, weight, true);

            gui.addMessage("Created PRIORITY package " + name +
                    " (travel " + travelTime + "s, " + weight + "kg)");

            for (String agent : readyAgents) {
                ACLMessage offer = new ACLMessage(ACLMessage.PROPOSE);
                offer.setContent(name + ":" + travelTime + ":" + weight + ":PRIORITY");
                offer.addReceiver(new AID(agent, AID.ISLOCALNAME));
                send(offer);
            }
            gui.addMessage("Broadcast PRIORITY offer to ALL agents");
        }
    }

    private void createPackage(String name, int travelTime, int weight) {
        PackageInfo pkg = new PackageInfo(name, travelTime, weight, false);
        availablePackages.put(name, pkg);
        gui.addMasterPackage(name, travelTime, weight, false);
    }

    private void createNewPackages(int count) {
        synchronized (availablePackages) {
            for (int i = 0; i < count; i++) {
                int pkgNum = (int) (Math.random() * 1000);
                String newPkgName = "P" + pkgNum;
                int travelTime = 2 + (int) (Math.random() * 5);
                int weight = 3 + (int) (Math.random() * 7);

                createPackage(newPkgName, travelTime, weight);
            }
            gui.addMessage("   -> Created " + count + " new packages | Available: " + availablePackages.size());
        }
    }

    private void pauseSystem() {
        systemPaused = true;
        gui.addMessage("");
        gui.addMessage("========================================");
        gui.addMessage("SYSTEM PAUSED");
        gui.addMessage("========================================");

        for (String agentName : readyAgents) {
            ACLMessage pauseMsg = new ACLMessage(ACLMessage.INFORM);
            pauseMsg.setContent("PAUSE");
            pauseMsg.addReceiver(new AID(agentName, AID.ISLOCALNAME));
            send(pauseMsg);
        }
    }

    private void resumeSystem() {
        systemPaused = false;
        gui.addMessage("");
        gui.addMessage("========================================");
        gui.addMessage("SYSTEM RESUMED");
        gui.addMessage("========================================");

        synchronized (availablePackages) {
            if (availablePackages.size() < 5) {
                createNewPackages(5 - availablePackages.size());
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
        boolean priority;

        PackageInfo(String n, int t, int w, boolean p) {
            name = n; travelTime = t; weight = w; priority = p;
        }
    }
}