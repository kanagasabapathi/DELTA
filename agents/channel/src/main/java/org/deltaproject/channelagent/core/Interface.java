package org.deltaproject.channelagent.core;

import org.deltaproject.channelagent.dummy.DummyController;
import org.deltaproject.channelagent.dummy.DummySwitch;
import org.deltaproject.channelagent.pkthandler.NIC;
import org.deltaproject.channelagent.pkthandler.PktHandler;
import org.deltaproject.channelagent.testcase.TestControllerCase;
import org.deltaproject.channelagent.testcase.TestSwitchCase;
import org.pcap4j.core.PcapNetworkInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;

public class Interface extends Thread {
    private static final Logger log = LoggerFactory.getLogger(Interface.class);

    private Socket socket;

    private InputStream in;
    private DataInputStream dis;
    private OutputStream out;
    private DataOutputStream dos;

    private Process processCbench;

    private String amIP;
    private int amPort;

    private PktHandler pktListener;
    private PcapNetworkInterface device;

    private byte ofVersion;
    private String ofPort;
    private String handler;
    private String cbench;

    private String controllerIP;
    private String switchIP;

    private TestControllerCase testController;
    private TestSwitchCase testSwitch;

    public Interface(String ip, String port) {
        amIP = ip;
        amPort = Integer.parseInt(port);
    }

    public boolean executeCbench() {
        try {
            cbench += "cbench";
            String[] cmdCbench = {cbench, "-c", this.controllerIP, "-p", ofPort, "-m", "1000", "-l", "20",
                    "-s", "32", "-M", "2000", "-t", "-o", "1000"};
            ProcessBuilder pb = new ProcessBuilder(cmdCbench);
            pb.redirectErrorStream(true);
            processCbench = pb.start();

            BufferedReader stderr = new BufferedReader(new InputStreamReader(processCbench.getInputStream()));

            String line;

            while ((line = stderr.readLine()) != null) {
                System.out.println(line);
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return true;
    }

    public void setConfiguration(String str) {
        String[] list = new String(str).split(",");
        String nic = "";

        for (String s : list) {
            if (s.startsWith("version")) {
                String ofVersion = s.substring(s.indexOf(":") + 1);
                if (ofVersion.equals("1.0"))
                    this.ofVersion = 1;
                else if (ofVersion.equals("1.3"))
                    this.ofVersion = 4;
            }

            if (s.startsWith("nic")) {
                nic = s.substring(s.indexOf(":") + 1);
                try {
                    this.device = NIC.getInterfaceByName(nic);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (s.startsWith("controller_ip")) {
                controllerIP = s.substring(s.indexOf(":") + 1);
            }

            if (s.startsWith("switch_ip")) {
                String temp = s.substring(s.indexOf(":") + 1);

                if (temp.contains(",")) {
                    switchIP = temp.substring(0, s.indexOf(","));
                } else {
                    switchIP = temp;
                }
            }

            if (s.startsWith("port")) {
                this.ofPort = s.substring(s.indexOf(":") + 1);
            }

            if (s.startsWith("handler")) {
                this.handler = s.substring(s.indexOf(":") + 1);
            }

            if (s.startsWith("cbench")) {
                this.cbench = s.substring(s.indexOf(":") + 1);
            }
        }

        log.info("[Channel Agent] Configuration setup completed");
        log.info("[Channel Agent] OpenFlow version/port\t: " + ofVersion + "/" + ofPort);
        log.info("[Channel Agent] MITM Network Interface\t: " + nic);
        log.info("[Channel Agent] Target Controller IP\t: " + controllerIP);
        log.info("[Channel Agent] Target Switch IP\t\t: " + switchIP);
        log.info("[Channel Agent] Cbench Root Path\t\t: " + cbench);

        pktListener = new PktHandler(device, controllerIP, switchIP, ofVersion, ofPort, handler);

        testController = new TestControllerCase(controllerIP, ofVersion, ofPort);
        testSwitch = new TestSwitchCase(controllerIP, ofVersion, ofPort);
    }

    public void connectManager() throws Exception {
        socket = new Socket(amIP, amPort);

        in = socket.getInputStream();
        dis = new DataInputStream(in);

        out = socket.getOutputStream();
        dos = new DataOutputStream(out);

        dos.writeUTF("ChannelAgent");
        dos.flush();

        String ack = dis.readUTF();

        if (ack.contains("OK")) {
            log.info("[Channel Agent] Connected with Agent-Manager");
        } else {
            log.info("[Channel Agent] Connection failed");
            System.exit(1);
        }
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        String recv = "";

        while (true) {
            try {
                this.connectManager();

                while (true) {
                    // reads characters encoded with modified UTF-8
                    recv = dis.readUTF();

                    if (recv.startsWith("config")) {
                        this.setConfiguration(recv);
                        continue;
                    }

                    /*
                     *  DATA_PLANE_OF test cases
                     */
                    if (recv.contains("runDummyController")) {
                        testSwitch.runDummyController(DummyController.HANDSHAKE_DEFAULT);
                        dos.writeUTF("runDummyController");
                    } else if (recv.equalsIgnoreCase("1.1.010")) {
                        String res = testSwitch.testPortRangeViolation(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("1.1.020")) {
                        String res = testSwitch.testTableID(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("1.1.030")) {
                        String res = testSwitch.testGroupID(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("1.1.040")) {
                        String res = testSwitch.testMeterID(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("1.1.050")) {
                        String res = testSwitch.testTableLoop(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("1.1.060")) {
                        String res = testSwitch.testCorruptedControlMsgType(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("1.1.070")) {
                        String res = testSwitch.testUnsupportedVersionNumber(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("1.1.080")) {
                        String res = testSwitch.testMalformedVersionNumber(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("1.1.090")) {
                        String res = testSwitch.testInvalidOXMType(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("1.1.100")) {
                        String res = testSwitch.testInvalidOXMLength(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("1.1.110")) {
                        String res = testSwitch.testInvalidOXMValue(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("1.1.120")) {
                        String res = testSwitch.testDisabledTableFeatureRequest(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("1.1.130")) {
                        String res = testSwitch.testHandshakeWithoutHello(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("1.1.140")) {
                        String res = testSwitch.testControlMsgBeforeHello(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("1.1.150")) {
                        String res = testSwitch.testIncompatibleHelloAfterConnection(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("1.1.160")) {
                        String res = testSwitch.testCorruptedCookieValue(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("1.1.170")) {
                        String res = testSwitch.testMalformedBufferIDValue(recv);
                        dos.writeUTF(res);
                    }

                    /*
                     *  CONTROL_PLANE_OF test cases
                     */
                    if (recv.contains("startsw")) {
                        if (recv.contains("nohello")) {
                            testController.startSW(DummySwitch.HANDSHAKE_NO_HELLO);
                        } else if (recv.contains("nohandshake")) {
                            testController.startSW(DummySwitch.NO_HANDSHAKE);
                        } else {
                            testController.startSW(DummySwitch.HANDSHAKE_DEFAULT);
                        }
                        dos.writeUTF("switchok");
                    } else if (recv.equalsIgnoreCase("2.1.010")) {
                        String res = testController.testMalformedVersionNumber(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("2.1.020")) {
                        String res = testController.testCorruptedControlMsgType(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("2.1.030")) {
                        String res = testController.testHandShakeWithoutHello(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("2.1.040")) {
                        String res = testController.testControlMsgBeforeHello(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("2.1.050")) {
                        String res = testController.testMultipleMainConnectionReq(recv);
                        dos.writeUTF(res);
                    } else if (recv.equalsIgnoreCase("2.1.060")) {
                        //pktListener.startListening();
                        String res = testController.testUnFlaggedFlowRemoveMsgNotification(recv);
                        dos.writeUTF(res);
                    } else if (recv.contains("2.1.070")) {
                        if (recv.contains("exit")) {
                            testController.exitTopo();
                            continue;
                        } else {
                            String res = testController.testTLSSupport(recv);
                            dos.writeUTF(res);
                        }
                    }

                    /*
                     *  ADVANCED test cases
                     */
//                    if (recv.equalsIgnoreCase("3.1.010")) {
//                        log.info("[Channel Agent] Pacekt-In Flooding test starts");
//                        this.executeCbench();
//                        dos.writeUTF("success");
//                    } else if (recv.equalsIgnoreCase("3.1.160")) {
//                        log.info("[Channel Agent] LinkFabrication test starts");
//                        pktListener.setTypeOfAttacks(TestCase.LINKFABRICATION);
//                        pktListener.startListening();
//                        pktListener.startARPSpoofing();
//
//                        Thread.sleep(40000);
//
//                        dos.writeUTF("success");
//                    } else if (recv.equalsIgnoreCase("3.1.170")) {
//                        log.info("[Channel Agent] Evaesdrop test starts");
//                        pktListener.setTypeOfAttacks(TestCase.EVAESDROP);
//                        pktListener.startListening();
//                        pktListener.startARPSpoofing();
//                    } else if (recv.equalsIgnoreCase("3.1.170-2")) {
//                        String result = pktListener.getTopoInfo();
//
//                        if (result != null && !result.isEmpty() && result.length() > 0)
//                            result = "success|\n:: Result for Building Topology ::\n" + result;
//                        else
//                            result = "fail";
//
//                        log.info("[Channel Agent] Topology Information " + result);
//                        dos.writeUTF(result);
//                    } else if (recv.equalsIgnoreCase("3.1.180")) {
//                        log.info("[Channel Agent] MITM test starts");
//                        pktListener.setTypeOfAttacks(TestCase.MITM);
//                        pktListener.startListening();
//                        pktListener.startARPSpoofing();
//                        dos.writeUTF("success");
//                    } else if (recv.equalsIgnoreCase("3.1.050")) { // Switch Table
//                        // Flooding
//                        testController.testSwitchTableFlooding();
//                        dos.writeUTF("success");
//                    } else if (recv.equalsIgnoreCase("3.1.060")) {
//                        log.info("[Channel Agent] Switch Identification Spoofing Test");
//                        pktListener.testSwitchIdentification();
//
//                        dos.writeUTF("success");
//                    }
//
//                    if (recv.contains("close")) {
//                        log.info("[Channel Agent] Closing...");
//                        dis.close();
//                        dos.close();
//                        System.exit(0);
//                    }

                    dos.flush();
                }
            } catch (ConnectException e) {
                log.error("[Channel-Agent] Agent Manager is not listening");
            } catch (Exception e) {
                // if any error occurs
                log.error(String.valueOf(e));
            } finally {

                try {
                    if (dis != null) {
                        dis.close();
                    }

                    if (dos != null) {
                        dos.close();
                    }

                    if (socket != null) {
                        socket.close();
                    }
                } catch (IOException e) {
                    log.error(e.toString());
                }
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
