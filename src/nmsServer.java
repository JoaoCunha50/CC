import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Arrays;

import PDU.NetTask;
import parser.Json_parser;

public class nmsServer {
    private DatagramSocket socket;
    private ConcurrentHashMap<Integer, InetSocketAddress> agentRegistry;
    private final static int PORT = 12345;
    private Map<Integer, byte[]> tasksMap;
    private ExecutorService clientThreadPool;

    public nmsServer() throws IOException {
        this.socket = new DatagramSocket(PORT);
        this.agentRegistry = new ConcurrentHashMap<>();
        this.clientThreadPool = Executors.newFixedThreadPool(10);

        Json_parser tasks = new Json_parser("src/tasks.json");
        try {
            this.tasksMap = tasks.tasks_parser();
        } catch (Exception e) {
            System.out.println("Error processing JSON File: " + e.getMessage());
        }
    }

    private void sendPacket(byte[] data, InetSocketAddress clientAddress) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, clientAddress.getAddress(),
                clientAddress.getPort());
        socket.send(packet);
    }

    private byte[] receivePacket() throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        socket.receive(packet);
        return Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
    }

    public void start() {
        System.out.println("Server started, waiting for packets...");

        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
                socket.receive(packet);

                clientThreadPool.submit(() -> handleClient(packet));
            } catch (IOException e) {
                System.out.println("[ERROR] Server error: " + e.getMessage());
            }
        }
    }

    private void handleClient(DatagramPacket packet) {
        try {
            byte[] data = packet.getData();
            InetSocketAddress clientAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());

            if (agentRegistry.containsValue(clientAddress)) {
                return;
            }

            int type = Byte.toUnsignedInt(data[data.length - 1]);

            if (type == NetTask.REGISTER) {
                int agentId = register(clientAddress);
                System.out.println("[REGISTER RECEIVED] Agent registered: ID = " + agentId);

                NetTask handler = new NetTask();
                byte[] ackPDU = handler.createAckPDU();
                sendPacket(ackPDU, clientAddress);
                System.out.println("[ACK SENT] Acknowledgement sent to agent " + agentId);
            }
        } catch (IOException e) {
            System.out.println("Error processing client: " + e.getMessage());
        }
        clientThreadPool.submit(() -> sendTasks());
    }

    private int register(InetSocketAddress clientAddress) {
        int agentId = agentRegistry.size() + 1;
        agentRegistry.put(agentId, clientAddress);
        return agentId;
    }

    private void sendTasks() {
        for (Map.Entry<Integer, byte[]> entry : tasksMap.entrySet()) {
            int agentID = entry.getKey();
            byte[] taskPDU = entry.getValue();

            // Process each task in a separate thread
            try {
                processTaskForAgent(agentID, taskPDU);
            } catch (IOException e) {
                // Handle task processing errors
                System.out.println("Error sending tasks: " + e.getMessage());
            }
        }
    }

    private void processTaskForAgent(int agentID, byte[] taskPDU) throws IOException {
        InetSocketAddress clientAddress = agentRegistry.get(agentID);
        if (clientAddress != null) {
            sendPacket(taskPDU, clientAddress);
            System.out.println("[TASK SENT] Task sent to agent " + agentID);
            tasksMap.remove(agentID);

            boolean ackReceived = false;
            int retries = 0;
            while (!ackReceived && retries < 3) {
                try {
                    byte[] response = receivePacket();
                    if (response != null && response.length > 0) {
                        int typeInt = Byte.toUnsignedInt(response[response.length - 1]);
                        if (typeInt == NetTask.ACKNOWLEDGE) {
                            ackReceived = true;
                            System.out.println("[ACK RECEIVED] ACK received from agent " + agentID);
                        }
                    }
                } catch (SocketTimeoutException e) {
                    retries++;
                    System.out.println(
                            "[RETRY] Retrying to send task to agent " + agentID + " (Attempt " + retries + ")");
                }
            }
            if (!ackReceived) {
                System.out.println("[FAILED] Failed to receive ACK from agent " + agentID + " after 3 attempts");
            }
        } else {
            System.out.println("[ERROR] Agent " + agentID + " is not registered.");
        }
    }

    public static void main(String[] args) {
        try {
            nmsServer server = new nmsServer();
            server.start();
        } catch (IOException e) {
            System.out.println("Error starting server: " + e.getMessage());
        }
    }
}
