import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import utils.*;
import PDU.NetTask;
import parser.Json_parser;

public class nmsServer {
    private DatagramSocket socket;
    private ServerSocket TCPsocket;
    private final static int PortaUDP = 12345;
    private final static int PortaTCP = 1234;
    private Map<Integer, InetSocketAddress> agentRegistry = new ConcurrentHashMap<>();
    private Map<Integer, Thread> agentThreads = new HashMap<>();
    private Map<Integer, byte[]> tasksMap = new ConcurrentHashMap<>();
    SeqManager seqNumbers = new SeqManager();
    private ReentrantLock lock = new ReentrantLock();

    public nmsServer() throws IOException {
        this.TCPsocket = new ServerSocket(PortaTCP);
        this.socket = new DatagramSocket(PortaUDP);
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

    private void handleClient(DatagramPacket packet) {
        try {
            byte[] dataEntry = packet.getData();
            byte[] data = Arrays.copyOfRange(dataEntry, 0, 38);
            InetSocketAddress clientAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());

            // Check if this client is already registered
            if (agentRegistry.containsValue(clientAddress)) {
                return;
            }

            int type = Byte.toUnsignedInt(data[data.length - 2]);

            if (type == NetTask.REGISTER) {
                // Synchronize the registration process
                int agentId = register(clientAddress);
                agentRegistry.put(agentId, clientAddress);
                seqNumbers.addRegistry(agentId, Byte.toUnsignedInt(data[data.length - 1]));

                System.out.println(
                        "[REGISTER RECEIVED] Agent registered: ID = " + agentId + " IP: "
                                + clientAddress.getAddress());

                int seqValue = seqNumbers.getSeqNumber(agentId);
                int seqNum = seqNumbers.getNextSeqNum(data, seqValue);
                seqNumbers.addToExistingValue(agentId, seqNum);

                NetTask handler = new NetTask();
                byte[] ackPDU = handler.createAckPDU(seqNum);
                sendPacket(ackPDU, clientAddress);

                System.out.println("[ACK SENT] Acknowledgement sent to agent " + agentId);

                sendTasks(agentId);
            }
        } catch (IOException e) {
            System.out.println("Error processing client: " + e.getMessage());
        }
    }

    private int register(InetSocketAddress clientAddress) {
        int agentId = agentRegistry.size() + 1;
        return agentId;
    }

    private void sendTasks(int id) {
        List<Integer> taskKeys = new ArrayList<>(tasksMap.keySet());
        for (Integer agentID : taskKeys) {
            if (agentID == id) {
                byte[] taskPDU = tasksMap.get(agentID);
                if (taskPDU != null) {
                    try {
                        processTaskForAgent(agentID, taskPDU);
                        // Remove the task after successful processing
                        tasksMap.remove(agentID);
                    } catch (IOException e) {
                        System.out.println("Error sending tasks: " + e.getMessage());
                    }
                }
            }
        }
    }

    private void processTaskForAgent(int agentID, byte[] taskPDU) throws IOException {
        InetSocketAddress clientAddress = agentRegistry.get(agentID);
        if (clientAddress != null) {
            int newSeq = seqNumbers.getSeqNumber(agentID);

            byte[] completeTask = insertSeqNumber(taskPDU,
                    (byte) newSeq);

            sendPacket(completeTask, clientAddress);
            System.out.println("[TASK SENT] Task sent to agent " + agentID);

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
                        if (typeInt == NetTask.ACKNOWLEDGE && ackValue == newSeq + completeTask.length) {
                            seqNumbers.addToExistingValue(agentID, ackValue);
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

    private void processMetrics(DatagramPacket packet) {
        try {
            byte[] dataEntry = packet.getData();
            byte[] data = Arrays.copyOfRange(dataEntry, 0, 38);
            InetSocketAddress clientAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());

            byte[] bufferTemp = Arrays.copyOfRange(dataEntry, 0, 38);
            String pduUUID = new String(Arrays.copyOfRange(bufferTemp, 0, 36), StandardCharsets.UTF_8);
            int type = Byte.toUnsignedInt(bufferTemp[36]);
            double output = Byte.toUnsignedInt(bufferTemp[37]);

            if (type == NetTask.METRICS) {
                System.out.println("[METRICS RECEIVED] Task Output received:");
                System.out.println("     taskUUID: " + pduUUID);
                System.out.println("     metrics:  " + output);
                System.out.println();

                NetTask handler = new NetTask();
                byte[] ackPDU = handler.createAckPDU(10);
                sendPacket(ackPDU, clientAddress);
                System.out.println("[ACK SENT] Acknowledgement sent to agent.");
            }
        } catch (IOException e) {
            System.out.println("Error processing metrics: " + e.getMessage());
        }
    }

    public void start() {
        System.out.println("Server started, waiting for packets...");

        // Enquanto o servidor estiver rodando, fica aguardando pacotes de agentes
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
                socket.receive(packet);

                // Verifica o tipo do pacote (tarefa ou métrica)
                byte[] data = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
                int type = Byte.toUnsignedInt(data[data.length - 2]); // Assume que o tipo está nos 2 últimos bytes

                // Se for uma métrica, processa
                if (type == NetTask.METRICS) {
                    processMetrics(packet);
                } else {
                    // Caso contrário, trata como uma requisição normal de tarefa
                    Thread agentThread = new Thread(() -> {
                        handleClient(packet);
                    });

                    int threadId = agentRegistry.size() + 1;
                    agentThreads.put(threadId, agentThread);
                    agentThread.start();
                }

            } catch (IOException e) {
                System.out.println("[ERROR] Server error: " + e.getMessage());
            }
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