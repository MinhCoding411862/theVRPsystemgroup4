package testCase_2;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class DeliveryAgent extends Agent {

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " ready to receive packages.");

        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
                ACLMessage msg = receive(mt);

                if (msg != null) {
                    String[] parts = msg.getContent().split(":");
                    String pkg = parts[0];
                    int deliveryTime = Integer.parseInt(parts[1]);
                    int timeToMaster = 8 + (int) (Math.random() * 5);//8 - 13s

                    SwingUtilities.invokeLater(() -> {
                        MasterAgent.gui.addMessage(getLocalName() + " picked up " + pkg);
                        MasterAgent.gui.updateAgentTimes(getLocalName(), timeToMaster, deliveryTime);
                    });

                    // Live countdown timer
                    Timer timer = new Timer(1000, null);
                    final int[] ttm = {timeToMaster};
                    final int[] dt = {deliveryTime};

                    timer.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            if (ttm[0] > 0) {
                                ttm[0]--;
                                MasterAgent.gui.updateAgentTimes(getLocalName(), ttm[0], dt[0]);
                            } else if (dt[0] > 0) {
                                dt[0]--;
                                MasterAgent.gui.updateAgentTimes(getLocalName(), 0, dt[0]);
                            } else {
                                timer.stop();
                                SwingUtilities.invokeLater(() -> {
                                    MasterAgent.gui.addMessage(getLocalName() + " finished delivery of " + pkg);
                                    MasterAgent.gui.updateAgentTimes(getLocalName(), 0, 0);
                                });
                            }
                        }
                    });
                    timer.start();
                } else {
                    block();
                }
            }
        });
    }
}
