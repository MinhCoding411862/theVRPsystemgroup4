package testCase_2;
import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

public class MasterAgent extends Agent {
    public static DeliveryGUI gui;

    @Override
    protected void setup() {
        gui = new DeliveryGUI();
        gui.addMessage("MasterAgent is ready.");

        // Create 3 Delivery Agents on the GUI
        for (int i = 1; i <= 3; i++) {
            String agentName = "Agent" + i;
            gui.addAgent(agentName);
        }

        // Simulate assigning 3 packages
        new Thread(() -> {
            try {
                Thread.sleep(1000); // wait before assigning

                for (int i = 1; i <= 3; i++) {
                    String pkg = "P" + i;
                    int deliveryTime = 10 + (int) (Math.random() * 6); // 10–16s
                    gui.addMasterPackage(pkg, deliveryTime);

                    ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                    msg.setContent(pkg + ":" + deliveryTime);
                    msg.addReceiver(new AID("Agent" + i, AID.ISLOCALNAME));
                    send(msg);

                    gui.addMessage("Assigned " + pkg + " to Agent" + i);
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
