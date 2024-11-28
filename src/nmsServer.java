import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import utils.*;
import PDU.NetTask;
import parser.Json_parser;

public class nmsServer {
    private DatagramSocket socket;
    private ConcurrentHashMap<Integer, InetSocketAddress> agentRegistry;
    private final static int PORT = 12345;
    private Map<Integer, byte[]> tasksMap;
    private ExecutorService clientThreadPool;
    SeqManager seqNumbers = new SeqManager();

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

    private List<Object> receivePacket() throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        socket.receive(packet);

        InetAddress clientAddress = packet.getAddress();
        int clientPort = packet.getPort(); // Obter a porta do cliente
        byte[] data = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());

        // Retorna dados e InetSocketAddress
        return Arrays.asList(data, new InetSocketAddress(clientAddress, clientPort));
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
            byte[] dataEntry = packet.getData();
            byte[] data = Arrays.copyOfRange(dataEntry, 0, 38);
            InetSocketAddress clientAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());

            if (agentRegistry.containsValue(clientAddress)) {
                return;
            }

            int type = Byte.toUnsignedInt(data[data.length - 2]);

            if (type == NetTask.REGISTER) {
                int agentId = register(clientAddress);
                agentRegistry.put(agentId, clientAddress);

                seqNumbers.addRegistry(agentId, Byte.toUnsignedInt(data[data.length - 1]));

                System.out.println("[REGISTER RECEIVED] Agent registered: ID = " + agentId);

                int seqValue = seqNumbers.getSeqNumber(agentId);
                NetTask handler = new NetTask();
                byte[] ackPDU = handler.createAckPDU(seqValue);
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
            int newSeq = seqNumbers.getSeqNumber(agentID) + (taskPDU.length + 1);
            seqNumbers.addToExistingValue(agentID, newSeq);

            byte[] completeTask = insertSeqNumber(taskPDU,
                    (byte) newSeq);

            sendPacket(completeTask, clientAddress);
            System.out.println("[TASK SENT] Task sent to agent " + agentID);
            tasksMap.remove(agentID);

            boolean ackReceived = false;
            int retries = 0;
            while (!ackReceived && retries < 3) {
                try {
                    List<Object> receivedData = receivePacket();
                    byte[] response = (byte[]) receivedData.get(0); // Dados do pacote
                    InetSocketAddress clientSocketAddress = (InetSocketAddress) receivedData.get(1); 

                    if (response != null && response.length > 0) {
                        int typeInt = Byte.toUnsignedInt(response[response.length - 2]);
                        int ackValue = Byte.toUnsignedInt(response[response.length - 1]);

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

    public static byte[] insertSeqNumber(byte[] originalArray, byte seqNumber) {
        int uuidLength = 36;
        int typeIndex = uuidLength;
        int seqIndex = typeIndex + 2;

        byte[] newArray = new byte[originalArray.length + 1];

        System.arraycopy(originalArray, 0, newArray, 0, seqIndex); // Copia UUID e Type
        newArray[seqIndex] = seqNumber;
        System.arraycopy(originalArray, seqIndex, newArray, seqIndex + 1, originalArray.length - seqIndex); // Copia
                                                                                                            // [Data]
        return newArray;
    }

    public void receiveMetrics() {
        try {
            while (true) {
                // Recebe os dados e o endereço do cliente com a porta
                List<Object> receivedData = receivePacket();
                byte[] defaultBuffer = (byte[]) receivedData.get(0);
                InetSocketAddress clientSocketAddress = (InetSocketAddress) receivedData.get(1);

                if (defaultBuffer.length > 0) {
                    byte[] bufferTemp = Arrays.copyOfRange(defaultBuffer, 0, 38);

                    byte[] pduUUIDBytes = Arrays.copyOfRange(bufferTemp, 0, 36);
                    String pduUUID = new String(pduUUIDBytes, StandardCharsets.UTF_8);

                    int type = Byte.toUnsignedInt(bufferTemp[36]);
                    int output = Byte.toUnsignedInt(bufferTemp[37]);

                    System.out.println("[METRICS RECEIVED] Task Output received:");
                    System.out.println("     taskUUID: " + pduUUID);
                    System.out.println("     metrics:  " + output);
                    System.out.println();

                    NetTask handler = new NetTask();
                    byte[] ackPDU = handler.createAckPDU(10);

                    // Envia ACK para o cliente usando InetSocketAddress
                    sendPacket(ackPDU, clientSocketAddress);
                    System.out.println("[ACK SENT] Acknowledgement sent to agent.");
                } else {
                    Thread.sleep(100);
                }
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Error receiving tasks: " + e.getMessage());
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
