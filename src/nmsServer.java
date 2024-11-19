import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Arrays;
import org.json.simple.parser.ParseException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import PDU.NetTask;
import parser.Json_parser;

public class nmsServer {
    private DatagramSocket socket;
    private ConcurrentHashMap<Integer, InetSocketAddress> agentRegistry;
    private int agentCounter = 1;
    private final static int port = 12345;
    private HashMap<Integer, byte[]> tasksMap;

    public nmsServer() throws IOException {
        this.socket = new DatagramSocket(port);
        socket.setSoTimeout(5000); // Tempo limite de 5 segundos
        this.agentRegistry = new ConcurrentHashMap<>();

        Json_parser tasks = new Json_parser("src/tasks.json");
        this.tasksMap = new HashMap<>();
        try {
            tasksMap = tasks.tasks_parser();
        } catch (ParseException e) {
            System.out.println("Error processing JSON File: " + e.getMessage());
            e.printStackTrace();
            return; // Interrompe o envio de tarefas se o JSON estiver inválido
        }
    }

    private void sendPacket(byte[] data, InetSocketAddress clientAddress) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, clientAddress.getAddress(),
                clientAddress.getPort());
        socket.send(packet);
    }

    public byte[] receivePacket() throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        try {
            socket.receive(packet); // Aguarda o pacote
            return Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
        } catch (SocketTimeoutException e) {
            System.out.println("[TIMEOUT] Nenhuma resposta recebida dentro do tempo limite.");
            return null; // Retorna nulo se o tempo esgotar
        }
    }

    public void start() {
        System.out.println("Server started, waiting for packets...");
        byte[] buffer = new byte[1024];

        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                handleClient(packet);
            } catch (IOException e) {
            }
        }
    }

    private void handleClient(DatagramPacket packet) {
        try {
            byte[] data = packet.getData();
            InetSocketAddress clientAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());
            int type = Byte.toUnsignedInt(data[data.length - 1]);

            if (type == NetTask.REGISTER) {
                int agentId = register(clientAddress);

                System.out.println(
                        "[REGISTER RECEIVED] Agent registered successfully\nID: " + agentId + "\nIP: "
                                + clientAddress);
                if (agentId != -1) {
                    NetTask handler = new NetTask();
                    byte[] ackPDU = handler.createAckPDU();

                    sendPacket(ackPDU, clientAddress);
                    System.out.println(
                        "[ACK SENT] REGISTER ACKOWLEDGEMENT sent");
                }
            }
        } catch (IOException e) {
            System.out.println("Error Processing Client: " + e.getMessage());
        }
        sendTasks();
    }

    private void sendTasks() {
        try {
            for (Map.Entry<Integer, byte[]> entry : tasksMap.entrySet()) {
                int agentID = entry.getKey();
                byte[] taskPDU = entry.getValue();

                if (agentRegistry.containsKey(agentID)) {
                    InetSocketAddress clientAddress = agentRegistry.get(agentID);
                    sendPacket(taskPDU, clientAddress);
                    System.out.println("[TASK SENT] Task sent to agent " + agentID);

                    boolean ackReceived = false;
                    while (!ackReceived) {
                        byte[] response = receivePacket(); // Recebe o pacote
                        if (response != null && response.length > 0) {
                            byte type = response[response.length - 1];
                            int typeInt = Byte.toUnsignedInt(type);
                            if (typeInt == NetTask.ACKNOWLEDGE) {
                                ackReceived = true;
                                System.out.println("[ACK RECEIVED] ACK received. Operation successful.\n");
                            }
                        }
                    }
                    // Após enviar a task com sucesso, remove a task do mapa
                    tasksMap.remove(agentID);
                } else {
                    System.out.println("Agent " + agentID + " is not registered.");
                }
            }
        } catch (IOException e) {
            System.out.println("Error sending tasks: " + e.getMessage());
        }
    }

    private int register(InetSocketAddress clientAddress) {
        if (!agentRegistry.containsValue(clientAddress)) { // Verifica se o agente já está registrado
            int agentId = agentCounter++;
            agentRegistry.put(agentId, clientAddress); // Adiciona o novo agente ao registro
            return agentId; // Retorna o ID atribuído ao agente
        } else {
            return -1;
        }
    }

    public static void main(String[] args) {
        try {
            nmsServer server = new nmsServer();
            server.start();
        } catch (IOException e) {
            System.out.println("Error starting the server: " + e.getMessage());
        }
    }
}
