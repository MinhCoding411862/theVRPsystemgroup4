package testCase_1;
import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.core.behaviours.CyclicBehaviour;

public class DeliveryAgent extends Agent {
    private int timeToMaster;
    private String assignedPackage;
    private boolean started = false;

    private void log(String message) {
        System.out.println("[" + getLocalName() + "] " + message);
        System.out.flush();
    }

    @Override
    protected void setup() {
        log("SETUP: Agent initializing...");

        Object[] args = getArguments();
        if (args != null && args.length >= 1) {
            timeToMaster = Integer.parseInt(args[0].toString());
            log("SETUP: timeToMaster = " + timeToMaster);
        } else {
            log("ERROR: No arguments provided");
            doDelete();
            return;
        }

        String agentNumber = getLocalName().replaceAll("[^0-9]", "");
        assignedPackage = "P" + agentNumber;
        log("SETUP: Assigned package = " + assignedPackage);

        try {
            Thread.sleep(1000);
            System.out.println("🚚 " + getLocalName() + " created (pickup time: " + timeToMaster +
                    "s, assigned: " + assignedPackage + ")");
            System.out.flush();
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        AID master = new AID("master", AID.ISLOCALNAME);
        ACLMessage readyMsg = new ACLMessage(ACLMessage.INFORM);
        readyMsg.addReceiver(master);
        readyMsg.setContent("READY");
        send(readyMsg);
        log("SETUP: Sent READY message to master");

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) {
                    block();
                    return;
                }

                log("BEHAVIOUR: Received message: " + msg.getContent());

                if (!started && "START".equals(msg.getContent())) {
                    started = true;
                    log("BEHAVIOUR: START signal received, launching delivery thread");
                    new Thread(() -> performDelivery()).start();
                }
            }
        });

        log("SETUP: CyclicBehaviour added, agent ready");
    }

    private void performDelivery() {
        try {
            log("DELIVERY: Thread started");

            // Step 1: Travel to master
            log("DELIVERY: Step 1 - Traveling to master");
            System.out.println("🚶 " + getLocalName() + " traveling to MasterAgent...");
            System.out.flush();
            Thread.sleep(1000 + timeToMaster * 2000);
            log("DELIVERY: Arrived at master");

            // Step 2: Request package
            log("DELIVERY: Step 2 - Requesting package " + assignedPackage);
            System.out.println("📞 " + getLocalName() + " requesting " + assignedPackage + " from MasterAgent...");
            System.out.flush();
            Thread.sleep(1000);

            AID master = new AID("master", AID.ISLOCALNAME);
            ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
            request.addReceiver(master);
            request.setContent(assignedPackage);
            send(request);
            log("DELIVERY: Request sent, waiting for reply");

            // Step 3: Wait for reply
            ACLMessage reply = blockingReceive();
            log("DELIVERY: Received reply - Performative: " + reply.getPerformative());

            if (reply.getPerformative() == ACLMessage.REFUSE) {
                log("DELIVERY: Package request REFUSED");
                System.out.println("❌ " + getLocalName() + " could not get " + assignedPackage);
                System.out.flush();
                return;
            }

            // Step 4: Package picked up
            int deliveryTime = Integer.parseInt(reply.getContent());
            log("DELIVERY: Step 3 - Package picked up, deliveryTime = " + deliveryTime);
            System.out.println("📦 " + getLocalName() + " picked up " + assignedPackage);
            System.out.flush();
            Thread.sleep(1000);

            // Step 5: Deliver package
            log("DELIVERY: Step 4 - Starting delivery");
            System.out.println("🚚 " + getLocalName() + " delivering " + assignedPackage + "...");
            System.out.flush();
            Thread.sleep(1000 + deliveryTime * 2000);
            log("DELIVERY: Delivery travel complete");

            // Step 6: Delivery complete
            log("DELIVERY: Step 5 - Printing completion message");
            System.out.println("✅ " + getLocalName() + " finished delivering " + assignedPackage);
            System.out.println();
            System.out.flush();
            Thread.sleep(500);
            log("DELIVERY: Completion message printed");

            // Step 7: Notify master
            log("DELIVERY: Step 6 - Sending DONE message to master");
            ACLMessage doneMsg = new ACLMessage(ACLMessage.INFORM);
            doneMsg.addReceiver(master);
            doneMsg.setContent("DONE");
            send(doneMsg);
            log("DELIVERY: DONE message sent");

            log("DELIVERY: Thread ending, agent will remain alive");

        } catch (InterruptedException e) {
            log("DELIVERY ERROR: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            log("DELIVERY EXCEPTION: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void takeDown() {
        log("TAKEDOWN: Agent shutting down");
        System.out.println("🛑 " + getLocalName() + " terminating.");
        System.out.flush();
    }
}