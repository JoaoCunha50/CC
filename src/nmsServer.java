import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.simple.parser.ParseException;

import PDU.NetTask;
import parser.Json_parser;

public class nmsServer {
    private DatagramSocket socket;
    private ConcurrentHashMap<Integer, InetSocketAddress> agentRegistry;
    private int agentCounter = 1;

    public nmsServer(int port) throws IOException {
        this.socket = new DatagramSocket(port);
        this.agentRegistry = new ConcurrentHashMap<>();
    }

    public void start() {
        System.out.println("Servidor iniciado e aguardando pacotes...");
        byte[] buffer = new byte[1024];

        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                new Thread(() -> handleClient(packet)).start();
                //new Thread(() -> sendTasks()).start();
            } catch (IOException e) {
                System.out.println("Erro ao receber pacote: " + e.getMessage());
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
                NetTask handler = new NetTask();
                byte[] ackPDU = handler.createAckPDU();

                sendPacket(ackPDU, clientAddress);
                System.out.println("Agente registrado. ID: " + agentId + " IP: " + clientAddress);
                sendTasks();
            }
        } catch (IOException e) {
            System.out.println("Erro ao processar cliente: " + e.getMessage());
        }
    }

    private void sendTasks() {
        try {
            Json_parser tasks = new Json_parser("src/tasks.json");
            HashMap<Integer, byte[]> tasksMap = new HashMap<>();

            try {
                tasksMap = tasks.tasks_parser();
            } catch (ParseException e) {
                System.out.println("Erro ao processar o arquivo JSON: " + e.getMessage());
                e.printStackTrace();
                return; // Interrompe o envio de tarefas se o JSON estiver inválido
            }

            for (Map.Entry<Integer, byte[]> entry : tasksMap.entrySet()) {
                int agentID = entry.getKey();
                byte[] taskPDU = entry.getValue();

                if (agentRegistry.containsKey(agentID)) {
                    InetSocketAddress clientAddress = agentRegistry.get(agentID);

                    sendPacket(taskPDU, clientAddress);
                    System.out.println("Task enviada para o agente ID " + agentID);

                    // Após enviar a task com sucesso, remove a task do mapa
                    tasksMap.remove(agentID);
                    System.out.println("Task removida do mapa para o agente ID " + agentID);
                } else {
                    System.out.println("Agente " + agentID + " não registrado.");
                }
            }
        } catch (IOException e) {
            System.out.println("Erro ao enviar tarefas: " + e.getMessage());
        }
    }

    private void sendPacket(byte[] data, InetSocketAddress clientAddress) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, clientAddress.getAddress(),
                clientAddress.getPort());
        socket.send(packet);
    }

    private int register(InetSocketAddress clientAddress) {
        int agentId = agentCounter++;
        agentRegistry.put(agentId, clientAddress);
        return agentId;
    }

    public static void main(String[] args) {
        int port = 12345;
        try {
            nmsServer server = new nmsServer(port);
            server.start();
        } catch (IOException e) {
            System.out.println("Erro ao iniciar o servidor: " + e.getMessage());
        }
    }
}
