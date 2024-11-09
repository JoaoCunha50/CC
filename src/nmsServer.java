import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.json.simple.parser.ParseException;

import PDU.NetTask;
import parser.Json_parser;

public class nmsServer {
    private ServerSocket serverSocket;
    private ConcurrentHashMap<Integer, InetAddress> agentRegistry;
    private int agentCounter = 1;

    public nmsServer(int port) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.agentRegistry = new ConcurrentHashMap<>();
    }

    public void start() {
        System.out.println("Servidor iniciado e aguardando conexões...");
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleClient(socket)).start();
            } catch (IOException e) {
                System.out.println("Erro ao aceitar conexão: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket socket) {
        try (ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {

            // Ler o pedido de registro do agente
            NetTask task = (NetTask) input.readObject();
            if (task.getType() == 1) {
                int agentId = register(task.getSenderNode());

                NetTask ackTask = new NetTask(
                        task.getUUID(),
                        socket.getInetAddress(),
                        task.getSenderNode(),
                        NetTask.ACKNOWLEDGE, // Tipo ACKNOWLEDGE
                        0, // Número de sequência
                        1, // Tamanho da janela
                        Integer.toString(agentId).getBytes() // ID do agente como dados
                );

                output.writeObject(ackTask);
                output.flush();
                System.out.println("Agente registrado com sucesso. ID: " + agentId);
            }

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Erro ao comunicar com o cliente: " + e.getMessage());
        }
    }

    private void sendTasks(Socket socket) {
        try (ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {

            Json_parser tasks = new Json_parser("../tasks.json");
            HashMap<String, NetTask> tasksMap = new HashMap<>();
            try {
                tasksMap = tasks.tasks_parser();
            } catch (ParseException e) {
                e.printStackTrace();
            }
            NetTask task_to_send = tasksMap.get("1a2b3c4d-5e6f-7g8h-9i0j-1k2l3m4n5o6p");
            output.writeObject(task_to_send);
            output.flush();

        } catch (IOException e) {
            System.out.println("Erro ao comunicar com o cliente: " + e.getMessage());
        }
    }

    private int register(InetAddress agentAddress) {
        int agentId = agentCounter++;
        agentRegistry.put(agentId, agentAddress);
        return agentId;
    }

    public static void main(String[] args) {
        int porta = 12345;
        try {
            nmsServer server = new nmsServer(porta);
            server.start();
        } catch (IOException e) {
            System.out.println("Erro ao iniciar o servidor: " + e.getMessage());
        }
    }
}
