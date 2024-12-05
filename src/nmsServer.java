import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.locks.ReentrantLock;

import utils.*;
import PDU.AlertFlow;
import PDU.NetTask;
import parser.Json_parser;

public class nmsServer {
    private DatagramSocket socket;
    private ServerSocket TCPsocket;
    private final static int PortaUDP = 12345;
    private final static int PortaTCP = 1234;
    private Map<String, InetSocketAddress> agentRegistry = new ConcurrentHashMap<>();
    private Map<Integer, Thread> agentThreads = new HashMap<>();
    private Map<String, List<byte[]>> tasksMap = new ConcurrentHashMap<>();
    private NetworkUtils utils;
    private List<Thread> ThreadList = new ArrayList<>();
    private ReentrantLock lock = new ReentrantLock();

    public nmsServer() throws IOException {
        this.TCPsocket = new ServerSocket(PortaTCP);
        this.socket = new DatagramSocket(PortaUDP);

        List<String> received_UUID = new ArrayList<>();
        this.utils = new NetworkUtils(socket, received_UUID);

        Json_parser tasks = new Json_parser("src/tasks.json");
        try {
            this.tasksMap = tasks.tasks_parser();
        } catch (Exception e) {
            System.out.println("Error processing JSON File: " + e.getMessage());
        }
    }

    private void handleTCPClient(Socket clientSocket) {
        try (InputStream input = clientSocket.getInputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                if (bytesRead > 0) {
                    byte[] data = Arrays.copyOf(buffer, bytesRead);
                    processAlerts(data, clientSocket);
                }
            }
        } catch (IOException e) {
            System.out.println("IOException handling client: " + e.getMessage());
        }
    }

    private void handleClient(byte[] data, InetSocketAddress clientSocketAddress) {

        int type = Byte.toUnsignedInt(data[data.length - 4]); // Ler o tipo (penúltimo byte antes de seqNum)
        if (type == NetTask.REGISTER) {
            byte[] uuid = Arrays.copyOfRange(data, 0, 36);
            String dataUUID = new String(uuid, StandardCharsets.UTF_8);
            // Ler os 3 bytes do seqNum
            byte[] seqBytes = Arrays.copyOfRange(data, data.length - 3, data.length); // Últimos 3 bytes
            int seqNum = ByteBuffer.wrap(new byte[] { 0, seqBytes[0], seqBytes[1], seqBytes[2] }).getInt();

            InetAddress clientAddress = clientSocketAddress.getAddress();

            // Synchronize the registration process
            if (!agentRegistry.containsValue(clientSocketAddress)) {
                utils.getReceived_UUID().add(dataUUID);
                String agentId = clientSocketAddress.getAddress().getHostName();
                agentRegistry.put(agentId, clientSocketAddress);

                utils.getSeqManager().add(clientAddress, seqNum);

                System.out.println("[REGISTER RECEIVED] Agent registered");
                System.out.println("        ID: " + agentId);
                System.out.println("        IP: " + clientAddress);
                System.out.println();

                int ackValue = utils.getSeqManager().getSeqNumber(clientAddress);
                int nextAckValue = utils.getSeqManager().getNextSeqNum(data, ackValue); // Gere o próximo seqNum
                utils.getSeqManager().add(clientAddress, nextAckValue);

                NetTask handler = new NetTask();
                byte[] ackPDU = handler.createAckPDU(nextAckValue, uuid);

                utils.sendPacket(ackPDU, clientSocketAddress);

                System.out.println("[ACK SENT] Acknowledgement sent to agent " + agentId);
                System.out.println("           UUID: " + dataUUID);
                System.out.println("           Ack Value: " + nextAckValue);
                System.out.println();

                sendTasks(agentId);
            } else {
                String agentId = getIDfromIP(clientSocketAddress);
                int nextSeqNum = utils.getSeqManager().getSeqNumber(clientAddress); // Gere o próximo seqNum

                NetTask handler = new NetTask();
                byte[] ackPDU = handler.createAckPDU(nextSeqNum, uuid); // Enviar o próximo seqNum no ACK

                utils.sendPacket(ackPDU, clientSocketAddress);

                sendTasks(agentId);
            }
        }
    }

    private void sendTasks(String id) {
        List<String> taskKeys = new ArrayList<>(tasksMap.keySet());
        for (String agentID : taskKeys) {
            if (agentID.equals(id)) {
                List<byte[]> taskPDU = tasksMap.get(agentID);
                if (taskPDU != null) {
                    try {
                        processTaskForAgent(agentID, taskPDU);
                        tasksMap.remove(agentID);
                    } catch (IOException e) {
                        System.out.println("IOException sending tasks: " + e.getMessage());
                    }
                }
            }
        }
    }

    private void processTaskForAgent(String agentID, List<byte[]> taskPDUs) throws IOException {
        lock.lock();
        try {
            // Verificar se o agente está registrado
            InetSocketAddress clientSocketAddress = agentRegistry.get(agentID);
            InetAddress clientAddress = clientSocketAddress.getAddress();

            for (byte[] task : taskPDUs) {
                int currentSeq = utils.getSeqManager().getSeqNumber(clientAddress); // Obter número de sequência atual
                                                                                    // para
                                                                                    // o agente
                String uuid = new String(Arrays.copyOfRange(task, 0, 36), StandardCharsets.UTF_8);
                // Inserir número de sequência de 3 bytes no pacote
                byte[] completeTask = insertSeqNumber(task, currentSeq);
                System.out.println("[TASK SENT] Task sent to agent " + agentID);
                System.out.println("            UUID: " + uuid);
                System.out.println("            Seq Num: " + currentSeq);
                System.out.println();
                utils.queuePacket(completeTask, clientSocketAddress);

                while (utils.isUUIDPending(uuid) != null) {
                    Thread.sleep(100);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }

    }

    public static byte[] insertSeqNumber(byte[] originalArray, int seqNumber) {
        int uuidLength = 36;
        int typeIndex = uuidLength; // Tipo vem logo após o UUID
        int seqIndex = typeIndex + 1; // Seq começa logo após o tipo

        // Criar novo array com espaço para o número de sequência (3 bytes adicionais)
        byte[] newArray = new byte[originalArray.length + 3];

        // Copiar partes do array original
        System.arraycopy(originalArray, 0, newArray, 0, seqIndex); // Copia UUID e Tipo

        byte[] seqBytes = ByteBuffer.allocate(4).putInt(seqNumber).array(); // Converter seqNumber para 4 bytes

        System.arraycopy(seqBytes, 1, newArray, seqIndex, 3); // Copiar apenas os 3 bytes menos significativos do seq
        System.arraycopy(originalArray, seqIndex, newArray, seqIndex + 3, originalArray.length - seqIndex); // Copiar o
                                                                                                            // resto

        return newArray;
    }

    private void processMetrics(byte[] dataEntry, InetSocketAddress clientAddress) {

        NetTask handler = new NetTask();

        byte[] bufferTemp = Arrays.copyOfRange(dataEntry, 0, 42);
        byte[] uuid = Arrays.copyOfRange(bufferTemp, 0, 36);
        String pduUUID = new String(Arrays.copyOfRange(bufferTemp, 0, 36), StandardCharsets.UTF_8);
        int type = Byte.toUnsignedInt(bufferTemp[36]);
        byte[] seqBytes = Arrays.copyOfRange(bufferTemp, 37, 40);
        int seqnum = ByteBuffer.wrap(new byte[] { 0, seqBytes[0], seqBytes[1], seqBytes[2] }).getInt();
        int taskType = Byte.toUnsignedInt(bufferTemp[40]);
        double output = Byte.toUnsignedInt(bufferTemp[41]);

        if (taskType == 5) { // para converter este output no seu valor real
            output /= 10;
        }

        String agentID = getIDfromIP(clientAddress);

        if (type == NetTask.METRICS && !utils.getReceived_UUID().contains(pduUUID)) {
            utils.getReceived_UUID().add(pduUUID);
            int ack_updated = utils.getSeqManager().getNextSeqNum(bufferTemp, seqnum);
            utils.getSeqManager().add(clientAddress.getAddress(), ack_updated);

            byte[] ackPDU = handler.createAckPDU(ack_updated, uuid);

            System.out.println("[METRICS RECEIVED] Task Output received:");
            System.out.println("     agentID:  " + agentID);
            System.out.println("     seqNum:  " + seqnum);
            System.out.println("     taskUUID: " + pduUUID);
            System.out.println("     taskType: " + taskType);
            System.out.println("     metrics:  " + output);
            System.out.println();

            utils.sendPacket(ackPDU, clientAddress);

            System.out.println("[ACK SENT] Acknowledgement sent to agent " + agentID);
            System.out.println("           UUID: " + pduUUID);
            System.out.println("           ackValue " + ack_updated);
            System.out.println();

            OutputHandler.saveMetricsToJson(agentID, pduUUID, output, taskType);
            return;

        } else if (type == NetTask.METRICS && utils.getReceived_UUID().contains(pduUUID)) {
            int seq = utils.getSeqManager().getSeqNumber(clientAddress.getAddress());
            byte[] ackPDU = handler.createAckPDU(seq, uuid);

            utils.sendPacket(ackPDU, clientAddress);
            return;
        }
    }

    private void processAlerts(byte[] dataEntry, Socket clientSocket) {
        try {
            NetTask handler = new NetTask();
            OutputStream output = clientSocket.getOutputStream();

            // Extrai informações do PDU
            byte[] bufferTemp = Arrays.copyOfRange(dataEntry, 0, 43);
            String pduUUID = new String(Arrays.copyOfRange(bufferTemp, 0, 36), StandardCharsets.UTF_8);
            byte[] uuid = Arrays.copyOfRange(bufferTemp, 0, 36);
            int type = Byte.toUnsignedInt(bufferTemp[36]);
            byte[] seqBytes = Arrays.copyOfRange(bufferTemp, 37, 40);
            int seqnum = ByteBuffer.wrap(new byte[] { 0, seqBytes[0], seqBytes[1], seqBytes[2] }).getInt();
            int taskType = Byte.toUnsignedInt(bufferTemp[40]);
            int threshold = Byte.toUnsignedInt(bufferTemp[41]);
            double outputMetric = Byte.toUnsignedInt(bufferTemp[42]);

            if (taskType == 5) {
                outputMetric /= 10; // Normaliza o valor, se necessário
            }
            InetAddress clientAdress = clientSocket.getInetAddress();
            InetSocketAddress clientSocketAdress = new InetSocketAddress(clientAdress, 0);

            String agentID = getIDfromIP(clientSocketAdress);

            // Processa alertas
            if (type == AlertFlow.ALERT && !utils.getReceived_UUID().contains(pduUUID)) {
                utils.getReceived_UUID().add(pduUUID);
                int ackUpdated = utils.getSeqManager().getNextSeqNum(bufferTemp, seqnum);
                utils.getSeqManager().add(clientAdress, ackUpdated);

                // Cria o ACK
                byte[] ackPDU = handler.createAckPDU(ackUpdated, uuid);

                // Envia o ACK pelo TCP
                output.write(ackPDU);
                output.flush();

                // Exibe e salva as métricas
                System.out.println("[ALERTFLOW RECEIVED] TASK OUTPUT EXCEEDED THRESHOLD:");
                System.out.println("           taskUUID: " + pduUUID);
                System.out.println("           seqNum:  " + seqnum);
                System.out.println("           metrics:  " + outputMetric);
                System.out.println("           threshold:  " + threshold);
                System.out.println("           taskType:  " + taskType);
                System.out.println();

                System.out.println("[ACK SENT] Acknowledgement sent to agent " + agentID);
                System.out.println("           UUID: " + pduUUID);
                System.out.println("           ackValue:  " + ackUpdated);
                System.out.println();

                OutputHandler.saveAlertsToJson(agentID, pduUUID, outputMetric, taskType, threshold);
                return;

            } else if (type == AlertFlow.ALERT && utils.getReceived_UUID().contains(pduUUID)) {
                int ackUpdated = utils.getSeqManager().getSeqNumber(clientAdress);

                // Cria o ACK
                byte[] ackPDU = handler.createAckPDU(ackUpdated, uuid);

                // Envia o ACK pelo TCP
                output.write(ackPDU);
                output.flush();
                return;
            }
        } catch (IOException e) {
            System.out.println("IOException processing alerts: " + e.getMessage());
        }
    }

    private void processEndConnection(int seqnum, InetSocketAddress clientAddress) {

        NetTask handler = new NetTask();
        byte[] endPDU = handler.createEndPDU(seqnum);
        String pduUUID = new String(Arrays.copyOfRange(endPDU, 0, 36), StandardCharsets.UTF_8);
        int type = Byte.toUnsignedInt(endPDU[36]);

        utils.queuePacket(endPDU, clientAddress);

        String agentID = getIDfromIP(clientAddress);
        System.out.println("[END CONNECTION] Sending end of connection message to " + agentID);
        System.out.println("                 UUID: " + pduUUID);
        System.out.println("                 type:  " + type);
        System.out.println();

    }

    public String getIDfromIP(InetSocketAddress value) {
        for (Map.Entry<String, InetSocketAddress> entry : agentRegistry.entrySet()) {
            if (entry.getValue().equals(value) || entry.getValue().getAddress().equals(value.getAddress())) {
                return entry.getKey(); // Retorna a chave correspondente ao valor
            }
        }
        return null; // Retorna null se o valor não for encontrado
    }

    // Método para fechar o servidor e liberar os recursos
    private void closeServer() {
        try {

            for (Thread t : ThreadList) {
                if (t.isAlive()) {
                    t.interrupt();
                }
            }

            // Fechar threads, se necessário
            for (Thread agentThread : agentThreads.values()) {
                if (agentThread.isAlive()) {
                    agentThread.interrupt(); // Interromper os threads de agentes
                }
            }

            // Fechar o socket TCP
            if (TCPsocket != null && !TCPsocket.isClosed()) {
                TCPsocket.close();
                System.out.println("[INFO] TCP Server socket closed.");
            }

            // Fechar o socket UDP
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("[INFO] UDP Server socket closed.");
            }

            Thread.sleep(2000);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        utils.stopThreads();
        System.out.println("[INFO] Server closing...");
        System.exit(0);
    }

    public void start() {
        System.out.println("Server started, waiting for packets...");
        System.out.println("UDP Port: " + PortaUDP);
        System.out.println("TCP Port: " + PortaTCP);
        System.out.println();

        Thread tcpListener = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket clientSocket = TCPsocket.accept();
                    // Criar uma nova thread para cada cliente TCP conectado
                    Thread clientHandlerThread = new Thread(() -> handleTCPClient(clientSocket));
                    ThreadList.add(clientHandlerThread);
                    clientHandlerThread.start();
                } catch (IOException e) {
                    System.out.println("IOException listening to TCP alerts: " + e.getMessage());
                }
            }
        });
        ThreadList.add(tcpListener);
        tcpListener.start();

        // Loop principal para aguardar pacotes UDP
        Thread udpListener = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    List<Object> dataObject = utils.receivePacket();
                    byte[] packet = (byte[]) dataObject.get(0);
                    InetSocketAddress address = (InetSocketAddress) dataObject.get(1);
                    if (packet.length > 0) {
                        // Verifica o tipo do pacote (tarefa ou métrica)
                        byte[] data = Arrays.copyOfRange(packet, 0, packet.length);
                        int type = Byte.toUnsignedInt(data[36]); // Assume que o tipo está nos 2 últimos bytes

                        // Se for uma métrica, processa
                        if (type == NetTask.METRICS) {
                            Thread metricsThread = new Thread(() -> {
                                processMetrics(packet, address);
                            });
                            ThreadList.add(metricsThread);
                            metricsThread.start();
                        } else {
                            // Caso contrário, trata como uma requisição normal de tarefa
                            Thread agentThread = new Thread(() -> {
                                handleClient(packet, address);
                            });
                            int threadId = agentRegistry.size() + 1;
                            agentThreads.put(threadId, agentThread);
                            agentThread.start();
                        }

                    } else {
                        Thread.sleep(100);
                    }
                } catch (IOException | InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

            }
        });
        ThreadList.add(udpListener);
        udpListener.start();

        Thread endConnectionThread = new Thread(() -> endConnection());
        ThreadList.add(endConnectionThread);
        endConnectionThread.start();
    }

    private void endConnection() {
        // Scanner para monitorar a entrada do teclado
        Scanner scanner = new Scanner(System.in);

        while (true) {
            String input = scanner.nextLine();
            if (input.equalsIgnoreCase("q")) {
                boolean connectionsAreActive = !agentRegistry.isEmpty();

                if (connectionsAreActive) {
                    for (Map.Entry<String, InetSocketAddress> entry : agentRegistry.entrySet()) {
                        InetSocketAddress clientAddress = entry.getValue();

                        int seq = utils.getSeqManager().getSeqNumber(clientAddress.getAddress());

                        processEndConnection(seq, clientAddress);
                    }
                }
                scanner.close();
                closeServer();
                System.out.println("Server shut down successfully.");
                break; // Exit the loop and end the server
            }
        }
    }

    public static void main(String[] args) {
        try {
            nmsServer server = new nmsServer();
            server.start();
        } catch (IOException e) {
            System.out.println("IOException starting server: " + e.getMessage());
        }
    }
}