package testCase_7;
import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class DeliveryAgent extends Agent {
    private int capacity;
    private int currentLoad = 0;
    private List<PackageInTransit> packagesCarrying = new ArrayList<>();
    private boolean isAtMaster = true;
    private boolean started = false;
    private boolean paused = false;

    private double speedFactor;
    private String speedType;

    @Override
    protected void setup() {
        // LẤY CAPACITY THEO TÊN AGENT
        String name = getLocalName();
        capacity = switch (name) {
            case "Agent1" -> 15;
            case "Agent2" -> 18;
            case "Agent3" -> 20;
            default -> 15;
        };

        // TỐC ĐỘ RIÊNG
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

        // GỬI READY
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
                        if ("PAUSE".equals(msg.getContent())) { paused = true; return; }
                        if ("RESUME".equals(msg.getContent())) { paused = false; if (isAtMaster) doWait(1000); requestPackage(); return; }
                        if (!started && "START".equals(msg.getContent())) {
                            started = true;
                            SwingUtilities.invokeLater(() -> {
                                MasterAgent.gui.updateAgentSpeed(getLocalName(), speedType, speedFactor);
                                MasterAgent.gui.updateAgentCapacity(getLocalName(), capacity); // CẬP NHẬT CAPACITY
                            });
                            requestPackage();
                            return;
                        }
                    }
                    if (msg.getPerformative() == ACLMessage.PROPOSE) { handlePackageOffer(msg); return; }
                    if (msg.getPerformative() == ACLMessage.REFUSE) { handleRefusal(msg); return; }
                } else block();
            }
        });
    }

    private void requestPackage() {
        if (!isAtMaster || paused) return;
        ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
        req.setContent("REQUEST:" + currentLoad + ":" + capacity);
        req.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
        send(req);
    }

    private void handlePackageOffer(ACLMessage msg) {
        String[] p = msg.getContent().split(":");
        String pkgName = p[0];
        int baseTime = Integer.parseInt(p[1]);
        int weight = Integer.parseInt(p[2]);

        if (currentLoad + weight > capacity) {
            requestPackage();
            return;
        }

        currentLoad += weight;
        packagesCarrying.add(new PackageInTransit(pkgName, baseTime, weight, speedFactor));

        ACLMessage confirm = new ACLMessage(ACLMessage.CONFIRM);
        confirm.setContent(pkgName);
        confirm.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
        send(confirm);

        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.updateAgentLoad(getLocalName(), currentLoad, capacity);
            MasterAgent.gui.updateAgentPackages(getLocalName(), getPackageNames());
        });

        if (currentLoad < capacity) {
            doWait(500);
            if (!paused) requestPackage();
        } else startDelivery();
    }

    private void handleRefusal(ACLMessage msg) {
        if ("NO_PACKAGES".equals(msg.getContent())) {
            doWait(2000);
            if (!paused) requestPackage();
        } else if ("OVERWEIGHT".equals(msg.getContent()) && !packagesCarrying.isEmpty()) {
            startDelivery();
        }
    }

    private void startDelivery() {
        isAtMaster = false;
        packagesCarrying.sort(Comparator.comparingInt(p -> p.actualTravelTime));
        SwingUtilities.invokeLater(() -> MasterAgent.gui.updateAgentStatus(getLocalName(), "DELIVERING"));
        deliverNextPackage();
    }
    private void deliverNextPackage() {
        if (packagesCarrying.isEmpty()) { returnToMaster(); return; }
        PackageInTransit pkg = packagesCarrying.get(0);
        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.addMessage(getLocalName() + " → " + pkg.name +
                    " (base " + pkg.baseTravelTime + "s → " + pkg.actualTravelTime + "s | " + speedType + ")");
            MasterAgent.gui.updateAgentTravelTime(getLocalName(), pkg.actualTravelTime);
        });

        Timer t = new Timer(1000, null);
        int[] left = {pkg.actualTravelTime};
        t.addActionListener(e -> {
            left[0]--;
            SwingUtilities.invokeLater(() -> MasterAgent.gui.updateAgentTravelTime(getLocalName(), left[0]));
            if (left[0] == 0) { t.stop(); completeDelivery(pkg); }
        });
        t.start();
    }

    private void completeDelivery(PackageInTransit pkg) {
        currentLoad -= pkg.weight;
        packagesCarrying.remove(pkg);
        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.updateAgentLoad(getLocalName(), currentLoad, capacity);
            MasterAgent.gui.updateAgentPackages(getLocalName(), getPackageNames());
        });

        ACLMessage m = new ACLMessage(ACLMessage.INFORM);
        m.setContent("DELIVERED:" + pkg.name);
        m.addReceiver(new AID("MasterAgent", AID.ISLOCALNAME));
        send(m);
        doWait(500);
        deliverNextPackage();
    }

    private void returnToMaster() {
        SwingUtilities.invokeLater(() -> MasterAgent.gui.updateAgentStatus(getLocalName(), "RETURNING"));
        Timer t = new Timer(1000, null);
        int[] left = {3};
        t.addActionListener(e -> {
            left[0]--;
            if (left[0] == 0) { t.stop(); arriveAtMaster(); }
        });
        t.start();
    }

    private void arriveAtMaster() {
        isAtMaster = true;
        SwingUtilities.invokeLater(() -> {
            MasterAgent.gui.updateAgentStatus(getLocalName(), "AT_MASTER");
            MasterAgent.gui.updateAgentTravelTime(getLocalName(), 0);
        });
        doWait(1000);
        if (!paused) requestPackage();
    }

    private String getPackageNames() {
        return packagesCarrying.isEmpty() ? "None" :
                packagesCarrying.stream().map(p -> p.name).collect(Collectors.joining(", "));
    }

    static class PackageInTransit {
        String name; int baseTravelTime; int actualTravelTime; int weight;
        PackageInTransit(String n, int b, int w, double f) {
            name = n; baseTravelTime = b; weight = w;
            actualTravelTime = Math.max(1, (int)Math.round(b * f));
        }
    }
}