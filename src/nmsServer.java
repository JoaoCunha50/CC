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
        /*Antiga try condition:
        ByteArrayOutputStream output = new ByteArrayOutputStream(socket.getOutputStream());
        ByteArrayInputStream input = new ByteArrayInputStream(socket.getInputStream())
                */ 
        try (DataOutputStream output = new DataOutputStream(socket.getOutputStream());
             DataInputStream input =   new DataInputStream(socket.getInputStream())) {
            /*
            // Ler o pedido de registro do agente
            byte[] registerPDU = (byte[]) input.readObject();
            int type = (int) registerPDU[0];*/

            byte[] buffer = new byte[37];
            input.read(buffer);

            byte type = buffer[36];
            int typeInt = Byte.toUnsignedInt(type);
            System.out.println("O valor de type é: " + typeInt);
            
            if (typeInt == NetTask.REGISTER) {
                int agentId = register(socket.getInetAddress());

                NetTask handler = new NetTask();
                byte[] ackPDU = handler.createAckPDU();

                output.write(ackPDU);
                output.flush();
                System.out.println("Agente registrado com sucesso. ID: " + agentId + "\nIP agent: " + socket.getInetAddress());
            }

        } catch (IOException e) {
        System.out.println("Erro ao comunicar com o cliente: " + e.getMessage());
    }
    }

    private void sendTasks(Socket socket) {
        try (ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {

            Json_parser tasks = new Json_parser("../tasks.json");
            HashMap<Integer, NetTask> tasksMap = new HashMap<>();
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
