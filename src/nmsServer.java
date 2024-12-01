import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

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
    private Map<Integer, List<byte[]>> tasksMap = new ConcurrentHashMap<>();
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
                List<byte[]> taskPDU = tasksMap.get(agentID);
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

    private void processTaskForAgent(int agentID, List<byte[]> taskPDUs) throws IOException {
        // Verificar se o agente está registrado
        InetSocketAddress clientAddress = agentRegistry.get(agentID);
        for (byte[] task : taskPDUs) {
            int newSeq = seqNumbers.getSeqNumber(agentID); // Obter número de sequência para o agente

            // Inserir número de sequência no pacote
            byte[] completeTask = insertSeqNumber(task, (byte) newSeq);

            // Enviar tarefa
            sendPacket(completeTask, clientAddress);
            System.out.println("[TASK SENT] Task sent to agent " + agentID);

            boolean ackReceived = false;
            int retries = 0;

            // Loop de retransmissão até receber ACK ou atingir limite de tentativas
            while (!ackReceived && retries < 3) {
                try {
                    // Receber resposta
                    List<Object> receivedData = receivePacket();
                    byte[] response = (byte[]) receivedData.get(0); // Dados do pacote
                    InetSocketAddress clientSocketAddress = (InetSocketAddress) receivedData.get(1);

                    if (response != null && response.length > 0) {
                        // Extração do tipo e valor do ACK
                        int typeInt = Byte.toUnsignedInt(response[response.length - 2]);
                        int ackValue = Byte.toUnsignedInt(response[response.length - 1]);

                        // Verificar se o pacote é um ACK e se o valor do ACK é válido
                        if (typeInt == NetTask.ACKNOWLEDGE && ackValue == newSeq + completeTask.length) {
                            seqNumbers.addToExistingValue(agentID, ackValue); // Atualizar número de sequência
                            ackReceived = true;
                            System.out.println("[ACK RECEIVED] ACK received from agent " + agentID);
                        }
                    }
                } catch (SocketTimeoutException e) {
                    retries++;
                    System.out.println(
                            "[RETRY] Retrying to send task to agent " + agentID + " (Attempt " + retries + ")");
                    sendPacket(completeTask, clientAddress); // Reenviar tarefa
                }
            }

            // Verificar se falhou após 3 tentativas
            if (!ackReceived) {
                System.out.println("[FAILED] Failed to receive ACK from agent " + agentID + " after 3 attempts");
            }
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
            InetSocketAddress clientAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());

            byte[] bufferTemp = Arrays.copyOfRange(dataEntry, 0, 39);
            String pduUUID = new String(Arrays.copyOfRange(bufferTemp, 0, 36), StandardCharsets.UTF_8);
            int type = Byte.toUnsignedInt(bufferTemp[36]);
            int taskType = Byte.toUnsignedInt(bufferTemp[37]);
            double output = Byte.toUnsignedInt(bufferTemp[38]);

            if (taskType == 5) { // para converter este output no seu valor real
                output /= 10;
            }

            if (type == NetTask.METRICS) {
                System.out.println("[METRICS RECEIVED] Task Output received:");
                System.out.println("     taskUUID: " + pduUUID);
                System.out.println("     metrics:  " + output);
                System.out.println();

                int ID = getIDfromIP(clientAddress);
                String agentID = "agent" + ID;
                saveMetricsToJson(agentID, pduUUID, output, taskType);

                NetTask handler = new NetTask();
                byte[] ackPDU = handler.createAckPDU(10);
                sendPacket(ackPDU, clientAddress);
                System.out.println("[ACK SENT] Acknowledgement sent to agent.");
            }
        } catch (IOException e) {
            System.out.println("Error processing metrics: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public void saveMetricsToJson(String agentName, String taskUUID, double metrics, int taskType) {
        try {
            String metricsDir = "metrics";
            String filePath = metricsDir + "/metrics.json";

            // Cria o diretório, caso não exista
            File directory = new File(metricsDir);
            if (!directory.exists()) {
                directory.mkdir();
            }

            // Cria um objeto JSON para armazenar os dados
            JSONObject existingData = new JSONObject();

            // Tenta ler o conteúdo existente no arquivo
            File jsonFile = new File(filePath);
            if (jsonFile.exists()) {
                // Lê o conteúdo do arquivo JSON
                String content = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
                if (!content.isBlank()) {
                    // Parse o conteúdo como um JSONObject
                    existingData = (JSONObject) JSONValue.parse(content);
                }
            }

            // Verifica se já existe uma lista de tasks para o agente
            JSONArray taskList = (JSONArray) existingData.get(agentName);
            if (taskList == null) {
                taskList = new JSONArray(); // Se não existir, cria uma nova lista
            }

            // Cria um objeto JSON para a nova task, incluindo taskType, taskUUID e metrics
            // na ordem correta
            JSONObject task = new JSONObject();
            task.put("taskType", taskType); // Adiciona o taskType
            task.put("metrics", metrics); // Adiciona as métricas
            task.put("taskUUID", taskUUID); // Adiciona o taskUUID

            // Adiciona a nova task na lista do agente
            taskList.add(task);

            // Atualiza a lista de tasks no objeto JSON do agente
            existingData.put(agentName, taskList);

            // Escreve o JSON atualizado no arquivo
            try (FileWriter writer = new FileWriter(filePath)) {
                writer.write(existingData.toJSONString());
            }

        } catch (Exception e) {
            System.err.println("Erro ao guardar as métricas no JSON: " + e.getMessage());
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

    public void start() {
        System.out.println("Server started, waiting for packets...");

        // Enquanto o servidor estiver rodando, fica aguardando pacotes de agentes
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
                socket.receive(packet);

                // Verifica o tipo do pacote (tarefa ou métrica)
                byte[] data = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
                int type = Byte.toUnsignedInt(data[data.length - 3]); // Assume que o tipo está nos 3 últimos bytes

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