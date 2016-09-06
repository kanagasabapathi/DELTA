package org.deltaproject.odlagent;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;

//import com.example.fuzzing.DataFuzzing;

public class Communication extends Thread {
    int result = 1;

    private AppAgent app;
    private Activator act;

    private Socket socket;
    private InputStream in;
    private DataInputStream dis;
    private OutputStream out;
    private DataOutputStream dos;

    private String serverIP;
    private int serverPort;

    // private DataFuzzing fuzzing;

    public Communication() {
        // fuzzing = new DataFuzzing();
    }

    public void setServerAddr(String ip, int port) {
        this.serverIP = ip;
        this.serverPort = port;
    }

    public void connectServer(String agent) {
        try {
            socket = new Socket(serverIP, serverPort);
            in = socket.getInputStream();
            dis = new DataInputStream(in);
            out = socket.getOutputStream();
            dos = new DataOutputStream(out);

            dos.writeUTF(agent);
            dos.flush();

        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void setAgent(AppAgent in) {
        this.app = in;
    }

    public void setActivator(Activator in) {
        this.act = in;
    }

    public void write(String in) {
        try {
            dos.writeUTF(in);
            dos.flush();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void replayingKnownAttack(String recv) throws IOException {
        String result = "";

        if (recv.contains("3.1.020")) {
            app.setControlMessageDrop();

            while (true) {
                if (!app.getDroppedPkt().contains("nothing")) {
                    break;
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            dos.writeUTF(app.getDroppedPkt());
        } else if (recv.contains("3.1.030")) {
            app.setInfiniteLoop();
            return;
        } else if (recv.contains("3.1.040")) {
            result = app.testInternalStorageAbuse();
            dos.writeUTF(result);
        } else if (recv.contains("3.1.070")) {
            result = app.testFlowRuleModification();
            dos.writeUTF(result);
        } else if (recv.contains("3.1.080")) {

			/* infinite? */
            if (recv.contains("false"))
                app.testFlowTableClearance(false);
            else
                app.testFlowTableClearance(true);

            return;
        } else if (recv.contains("3.1.090")) {
            result = act.testEventListenerUnsubscription("arp");
            dos.writeUTF(result);
        } else if (recv.contains("3.1.100")) {
            result = act.testApplicationEviction("arp");
            dos.writeUTF(result);
        } else if (recv.contains("3.1.110")) {
            app.testResourceExhaustionMem();
            return;
        } else if (recv.contains("3.1.120")) {
            app.testResourceExhaustionCPU();
            return;
        } else if (recv.contains("3.1.130")) {
            app.testSystemVariableManipulation();
            return;
        } else if (recv.contains("3.1.140")) {
            app.testSystemCommandExecution();
            return;
        } else if (recv.contains("3.1.190")) {
            app.testFlowRuleFlooding();
            return;
        } else if (recv.contains("3.1.200")) {
            result = app.testSwitchFirmwareMisuse();
            dos.writeUTF(result);
        } else if (recv.contains("2.1.060")) {
            result = app.sendUnFlaggedRemoveMsg();
            dos.writeUTF(result);
        }

        dos.flush();
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        String recv = "";

        try {
            while ((recv = dis.readUTF()) != null) {
                // reads characters encoded with modified UTF-8
                replayingKnownAttack(recv);
            }
        } catch (Exception e) {
            // if any error occurs
            e.printStackTrace();
        }
    }
}