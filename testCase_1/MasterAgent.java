package testCase_1;
import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.core.behaviours.CyclicBehaviour;
import java.util.*;

public class MasterAgent extends Agent {
    private final Map<String, Integer> packages = new LinkedHashMap<>();
    private final int TOTAL_PACKAGES = 3;
    private final int TOTAL_AGENTS = 3;
    private final List<AID> readyAgents = new ArrayList<>();
    private boolean systemReady = false;

    @Override
    protected void setup() {
        // Initial delay to show text responsibly
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Create packages with randomized delivery time
        Random rand = new Random();
        System.out.println("📦 MasterAgent: Generating " + TOTAL_PACKAGES + " packages...\n");

        try {
            for (int i = 1; i <= TOTAL_PACKAGES; i++) {
                String packageName = "P" + i;
                int deliveryTime = rand.nextInt(10) + 1; // 1-10 seconds
                packages.put(packageName, deliveryTime);
                System.out.println(packageName + " with delivery time " + deliveryTime + "s created.");
                Thread.sleep(1000);
            }
            System.out.println("\n✅ All " + TOTAL_PACKAGES + " packages created successfully.\n");
            Thread.sleep(1000);
            System.out.println("Waiting for delivery agents to be ready...\n");
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Add behaviour to handle messages from delivery agents
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) {
                    block();
                    return;
                }

                // Handle READY message from delivery agents
                if (msg.getPerformative() == ACLMessage.INFORM && "READY".equals(msg.getContent())) {
                    handleAgentReady(msg.getSender());
                    return;
                }

                // Handle REQUEST from delivery agent asking for their assigned package
                if (msg.getPerformative() == ACLMessage.REQUEST) {
                    handlePackageRequest(msg);
                }
            }
        });
    }

    private void handleAgentReady(AID agent) {
        if (!readyAgents.contains(agent)) {
            readyAgents.add(agent);
            System.out.println("✅ " + agent.getLocalName() + " is ready.");

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // When all agents are ready, signal them to start
        if (!systemReady && readyAgents.size() >= TOTAL_AGENTS) {
            systemReady = true;
            try {
                System.out.println("\n✅ All agents ready. Starting delivery operations...\n");
                Thread.sleep(1000);

                // Notify all agents to start delivery
                for (AID aid : readyAgents) {
                    ACLMessage startMsg = new ACLMessage(ACLMessage.INFORM);
                    startMsg.addReceiver(aid);
                    startMsg.setContent("START");
                    send(startMsg);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void handlePackageRequest(ACLMessage msg) {
        String requestedPackage = msg.getContent();
        ACLMessage reply = msg.createReply();

        if (!packages.containsKey(requestedPackage)) {
            reply.setPerformative(ACLMessage.REFUSE);
            reply.setContent("Package not available");
            send(reply);
            System.out.println("❌ " + requestedPackage + " not available for " + msg.getSender().getLocalName());
            return;
        }

        int deliveryTime = packages.remove(requestedPackage);
        reply.setPerformative(ACLMessage.AGREE);
        reply.setContent(String.valueOf(deliveryTime));
        send(reply);

        try {
            System.out.println("📦 " + msg.getSender().getLocalName() + " picked up " + requestedPackage +
                    " (delivery time: " + deliveryTime + "s)");
            Thread.sleep(1000);

            if (packages.isEmpty()) {
                System.out.println("✅ MasterAgent has 0 packages left.\n");
            } else {
                System.out.println("MasterAgent has " + packages.size() + " package(s) remaining.\n");
            }
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void takeDown() {
        try {
            Thread.sleep(500); // Small delay before showing termination message
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("🛑 MasterAgent terminating.");
    }
}