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
import java.util.concurrent.locks.ReentrantLock;
import java.util.Scanner;

import utils.*;
import PDU.AlertFlow;
import PDU.NetTask;
import parser.Json_parser;

public class nmsServer {
    private DatagramSocket socket;
    private ServerSocket TCPsocket;
    private final static int PortaUDP = 12345;
    private final static int PortaTCP = 1234;
    private Map<Integer, InetSocketAddress> agentRegistry = new ConcurrentHashMap<>();
    private Map<Integer, Thread> agentThreads = new HashMap<>();
    private Map<Integer, List<byte[]>> tasksMap = new ConcurrentHashMap<>();
    private List<String> received_UUID = new ArrayList<>();
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
        String uuid = Arrays.copyOfRange(data, 0, 36).toString();

        if (!received_UUID.contains(uuid)) {
            received_UUID.add(uuid);
        }

        // Retorna dados e InetSocketAddress
        return Arrays.asList(data, new InetSocketAddress(clientAddress, clientPort));
    }

    private boolean sendWithRetry(byte[] data, InetSocketAddress clientAddress, int maxRetries) {
        int retryCount = 0;
        while (retryCount < maxRetries) {
            try {
                sendPacket(data, clientAddress);
                // Wait for acknowledgment with timeout
                if (waitForAcknowledgment(data)) {
                    return true;
                }

                // Apply exponential backoff
                Thread.sleep(1000);
                retryCount++;
            } catch (IOException | InterruptedException e) {
                System.out.println("[RETRY] Transmission error: " + e.getMessage());
                retryCount++;
            }
        }
        return false;
    }

    private boolean waitForAcknowledgment(byte[] pdu) throws IOException {
        try {
            socket.setSoTimeout(5000); // 5-second timeout
            List<Object> data = receivePacket();
            byte[] response = (byte[]) data.get(0);
            InetSocketAddress clientAddress = (InetSocketAddress) data.get(1);
            String uuid = Arrays.copyOfRange(response, 0, 36).toString();
            int typeInt = Byte.toUnsignedInt(response[response.length - 4]); // Ler o tipo (4º byte antes do
                                                                             // final)
            int agentID = getIDfromIP(clientAddress);

            // Ler os 3 últimos bytes do seqNum (ACK)
            byte[] ackBytes = Arrays.copyOfRange(response, response.length - 3, response.length);
            int ackInt = ByteBuffer.wrap(new byte[] { 0, ackBytes[0], ackBytes[1], ackBytes[2] }).getInt();

            // Validate acknowledgment logic here
            return validateAcknowledgment(typeInt, ackInt, pdu, seqNumbers.getSeqNumber(agentID), agentID);
        } catch (SocketTimeoutException e) {
            return false;
        } finally {
            socket.setSoTimeout(0); // Reset timeout
        }
    }

    public boolean validateAcknowledgment(int type, int ackValue, byte[] pdu, int seqnum_atual, int agentID) {
        lock.lock();
        try {
            if (type == NetTask.ACKNOWLEDGE && ackValue == (seqnum_atual + pdu.length)) {
                seqNumbers.addToExistingValue(agentID, ackValue);
                System.out.println(ackValue);
                return true;
            } else {
                return false;
            }
        } finally {
            lock.unlock();
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
            System.out.println("[ERROR] Error handling TCP client: " + e.getMessage());
        }
    }

    private void handleClient(DatagramPacket packet) {
        try {
            byte[] dataEntry = packet.getData();
            byte[] data = Arrays.copyOfRange(dataEntry, 0, 40);
            String dataUUID = Arrays.copyOfRange(dataEntry, 0, 36).toString();
            InetSocketAddress clientAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());

            int type = Byte.toUnsignedInt(data[data.length - 4]); // Ler o tipo (penúltimo byte antes de seqNum)
            if (type == NetTask.REGISTER) {
                // Ler os 3 bytes do seqNum
                byte[] seqBytes = Arrays.copyOfRange(data, data.length - 3, data.length); // Últimos 3 bytes
                int seqNum = ByteBuffer.wrap(new byte[] { 0, seqBytes[0], seqBytes[1], seqBytes[2] }).getInt();

                // Synchronize the registration process
                if (!agentRegistry.containsValue(clientAddress) && !received_UUID.contains(dataUUID)) {
                    received_UUID.add(dataUUID);
                    int agentId = register(clientAddress);
                    agentRegistry.put(agentId, clientAddress);

                    seqNumbers.addRegistry(agentId, seqNum);

                    System.out.println(
                            "[REGISTER RECEIVED] Agent registered: ID = " + agentId + " IP: "
                                    + clientAddress.getAddress());

                    int seqValue = seqNumbers.getSeqNumber(agentId);
                    int nextSeqNum = seqNumbers.getNextSeqNum(data, seqValue); // Gere o próximo seqNum
                    seqNumbers.addToExistingValue(agentId, nextSeqNum);

                    NetTask handler = new NetTask();
                    byte[] ackPDU = handler.createAckPDU(nextSeqNum);

                    sendPacket(ackPDU, clientAddress);

                    System.out.println("[ACK SENT] Acknowledgement sent to agent " + agentId);

                    sendTasks(agentId);
                } else {
                    int agentId = getIDfromIP(clientAddress);
                    int nextSeqNum = seqNumbers.getNextSeqNum(data, seqNum); // Gere o próximo seqNum
                    seqNumbers.addToExistingValue(agentId, nextSeqNum);

                    NetTask handler = new NetTask();
                    byte[] ackPDU = handler.createAckPDU(nextSeqNum); // Enviar o próximo seqNum no ACK

                    sendPacket(ackPDU, clientAddress);

                    sendTasks(agentId);
                }
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
                List<byte[]> taskPDU = tasksMap.get(agentID);
                if (taskPDU != null) {
                    try {
                        processTaskForAgent(agentID, taskPDU);
                        tasksMap.remove(agentID);
                    } catch (IOException e) {
                        System.out.println("Error sending tasks: " + e.getMessage());
                    }
                }
            }
        }
    }

    private void processTaskForAgent(int agentID, List<byte[]> taskPDUs) throws IOException {
        // Verificar se o agente está registrado
        InetSocketAddress clientAddress = agentRegistry.get(agentID);

        for (byte[] task : taskPDUs) {
            int currentSeq = seqNumbers.getSeqNumber(agentID); // Obter número de sequência atual para o agente

            // Inserir número de sequência de 3 bytes no pacote
            byte[] completeTask = insertSeqNumber(task, currentSeq);

            // Enviar tarefa ao agente
            boolean ackReceived = false;
            ackReceived = sendWithRetry(completeTask, clientAddress, 5);
            System.out.println("[TASK SENT] Task sent to agent " + agentID);

            if (ackReceived) {
                System.out.println("[ACK RECEIVED] ACK received from agent " + agentID);
            }
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

    private void processMetrics(DatagramPacket packet) {
        lock.lock();
        try {
            NetTask handler = new NetTask();
            byte[] dataEntry = packet.getData();
            InetSocketAddress clientAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());

            byte[] bufferTemp = Arrays.copyOfRange(dataEntry, 0, 42);
            String pduUUID = new String(Arrays.copyOfRange(bufferTemp, 0, 36), StandardCharsets.UTF_8);
            int type = Byte.toUnsignedInt(bufferTemp[36]);
            byte[] seqBytes = Arrays.copyOfRange(bufferTemp, 37, 40);
            int seqnum = ByteBuffer.wrap(new byte[] { 0, seqBytes[0], seqBytes[1], seqBytes[2] }).getInt();
            int taskType = Byte.toUnsignedInt(bufferTemp[40]);
            double output = Byte.toUnsignedInt(bufferTemp[41]);

            if (taskType == 5) { // para converter este output no seu valor real
                output /= 10;
            }

            int agentID = getIDfromIP(clientAddress);

            if (type == NetTask.METRICS && !received_UUID.contains(pduUUID)) {
                received_UUID.add(pduUUID);
                int ack_updated = seqNumbers.getNextSeqNum(bufferTemp, seqnum);
                seqNumbers.addToExistingValue(agentID, ack_updated);
                System.out.println(ack_updated + " + " + seqnum);
                byte[] ackPDU = handler.createAckPDU(ack_updated);
                int retries = 0;
                while (retries < 3) {
                    sendPacket(ackPDU, clientAddress);
                    retries++;
                }

                System.out.println("[METRICS RECEIVED] Task Output received:");
                System.out.println("     agentID:  " + agentID);
                System.out.println("     taskUUID: " + pduUUID);
                System.out.println("     taskType: " + taskType);
                System.out.println("     metrics:  " + output);
                System.out.println();

                System.out.println("[ACK SENT] Acknowledgement sent to agent " + agentID);

                String agentID_String = "agent" + agentID;
                OutputHandler.saveMetricsToJson(agentID_String, pduUUID, output, taskType);

            } else if (type == NetTask.METRICS && received_UUID.contains(pduUUID)) {
                int seq_updated = seqNumbers.getNextSeqNum(bufferTemp, seqnum);
                seqNumbers.addToExistingValue(agentID, seq_updated);
                byte[] ackPDU = handler.createAckPDU(seq_updated);
                int retries = 0;
                while (retries < 3) {
                    sendPacket(ackPDU, clientAddress);
                    retries++;
                }
            }
        } catch (IOException e) {
            System.out.println("Error processing metrics: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private void processAlerts(byte[] dataEntry, Socket clientSocket) {
        lock.lock();
        try {
            NetTask handler = new NetTask();
            OutputStream output = clientSocket.getOutputStream();

            // Extrai informações do PDU
            byte[] bufferTemp = Arrays.copyOfRange(dataEntry, 0, 43);
            String pduUUID = new String(Arrays.copyOfRange(bufferTemp, 0, 36), StandardCharsets.UTF_8);
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

            int agentID = getIDfromTCPIP(clientAdress);

            // Processa alertas
            if (type == AlertFlow.ALERT && !received_UUID.contains(pduUUID)) {
                received_UUID.add(pduUUID);
                int seqUpdated = seqNumbers.getNextSeqNum(bufferTemp, seqnum);
                seqNumbers.addToExistingValue(agentID, seqUpdated);

                // Cria o ACK
                byte[] ackPDU = handler.createAckPDU(seqUpdated);

                // Envia o ACK pelo TCP
                output.write(ackPDU);
                output.flush();

                // Exibe e salva as métricas
                System.out.println("[ALERTFLOW] TASK OUTPUT EXCEEDED THRESHOLD:");
                System.out.println("     taskUUID: " + pduUUID);
                System.out.println("     metrics:  " + outputMetric);
                System.out.println("     threshold:  " + threshold);
                System.out.println("     taskType:  " + taskType);
                System.out.println();

                System.out.println("[ACK SENT] Acknowledgement sent to agent " + agentID);

                String agentID_String = "agent" + agentID;
                OutputHandler.saveMetricsToJson(agentID_String, pduUUID, outputMetric, taskType);

            } else if (type == AlertFlow.ALERT && received_UUID.contains(pduUUID)) {
                int ackUpdated = seqNumbers.getNextSeqNum(bufferTemp, seqnum);
                seqNumbers.addToExistingValue(agentID, ackUpdated);

                // Cria o ACK
                byte[] ackPDU = handler.createAckPDU(ackUpdated);

                // Envia o ACK pelo TCP
                output.write(ackPDU);
                output.flush();

                // Exibe e salva as métricas
                System.out.println("[ALERTFLOW] TASK OUTPUT EXCEEDED THRESHOLD:");
                System.out.println("     taskUUID: " + pduUUID);
                System.out.println("     metrics:  " + outputMetric);
                System.out.println("     threshold:  " + threshold);
                System.out.println("     taskType:  " + taskType);
                System.out.println();

                System.out.println("[ACK SENT] Acknowledgement sent to agent " + agentID);
            }
        } catch (IOException e) {
            System.out.println("[ERROR] Error processing alert: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private void processEndConnection(int seqnum, InetSocketAddress clientAddress) {
        try {
            NetTask handler = new NetTask();
            byte[] endPDU = handler.createEndPDU(seqnum);
            String pduUUID = new String(Arrays.copyOfRange(endPDU, 0, 36), StandardCharsets.UTF_8);

            sendPacket(endPDU, clientAddress);

            int agentID = getIDfromIP(clientAddress);
            System.out.println("[END CONNECTION] Sending end of connection message to " + agentID);
            System.out.println("     taskUUID: " + pduUUID);
            System.out.println("     Sequence Number:  " + seqnum);
            System.out.println();

        } catch (IOException e) {
            System.out.println("[ERROR] Error sending endPDU " + e.getMessage());
        }
    }

    public Integer getIDfromIP(InetSocketAddress value) {
        for (Map.Entry<Integer, InetSocketAddress> entry : agentRegistry.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey(); // Retorna a chave correspondente ao valor
            }
        }
        return null; // Retorna null se o valor não for encontrado
    }

    public Integer getIDfromTCPIP(InetAddress value) {
        for (Map.Entry<Integer, InetSocketAddress> entry : agentRegistry.entrySet()) {
            if (entry.getValue().getAddress().equals(value)) {
                return entry.getKey(); // Retorna a chave correspondente ao valor
            }
        }
        return null; // Retorna null se o valor não for encontrado
    }

    // Método para fechar o servidor e liberar os recursos
    private void closeServer() {
        try {
            // Fechar o socket TCP
            if (TCPsocket != null && !TCPsocket.isClosed()) {
                TCPsocket.close();
                System.out.println("TCP Server socket closed.");
            }

            // Fechar o socket UDP
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("UDP Server socket closed.");
            }

            // Fechar threads, se necessário
            for (Thread agentThread : agentThreads.values()) {
                if (agentThread.isAlive()) {
                    agentThread.interrupt(); // Interromper os threads de agentes
                }
            }

            Thread.sleep(2000);
        } catch (IOException | InterruptedException e) {
            System.out.println("[ERROR] Error while closing server: " + e.getMessage());
        }
        System.out.println("Server closing...");
        System.exit(0);
    }

    public void start() {
        System.out.println("Server started, waiting for packets...");
        System.out.println("UDP Port: " + PortaUDP);
        System.out.println("TCP Port: " + PortaTCP);

        // Thread para aceitar conexões TCP
        Thread tcpListener = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try (Socket clientSocket = TCPsocket.accept()) {
                    handleTCPClient(clientSocket);
                } catch (IOException e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        System.out.println("[ERROR] TCP Server error: " + e.getMessage());
                    }
                }
            }
        });
        tcpListener.start();

        // Loop principal para aguardar pacotes UDP
        Thread udpListener = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
                    socket.receive(packet);

                    // Verifica o tipo do pacote (tarefa ou métrica)
                    byte[] data = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
                    int type = Byte.toUnsignedInt(data[36]); // Assume que o tipo está nos 2 últimos bytes

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
                    if (!Thread.currentThread().isInterrupted()) {
                        System.out.println("[ERROR] Server error: " + e.getMessage());
                    }
                }
            }
        });
        udpListener.start();

        // Scanner para monitorar a entrada do teclado
        Scanner scanner = new Scanner(System.in);

        // Monitorar entrada do teclado para fechar o servidor
        Thread inputMonitor = new Thread(() -> {
            while (true) {
                String input = scanner.nextLine();
                if (input.equalsIgnoreCase("q")) {
                    boolean connectionsAreActive = !agentRegistry.isEmpty();

                    if (connectionsAreActive) {
                        for (Map.Entry<Integer, InetSocketAddress> entry : agentRegistry.entrySet()) {
                            int agentID = entry.getKey();
                            InetSocketAddress clientAddress = entry.getValue();

                            int seq = seqNumbers.getSeqNumber(agentID);

                            processEndConnection(seq, clientAddress);
                        }
                    }
                    tcpListener.interrupt();
                    udpListener.interrupt();
                    closeServer();
                    System.out.println("Server shut down successfully.");
                    break; // Exit the loop and end the server
                }
            }
        });
        inputMonitor.start();
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