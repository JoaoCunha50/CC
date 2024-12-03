import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import utils.*;
import PDU.AlertFlow;
import PDU.NetTask;

public class nmsAgent {
    private DatagramSocket socket;
    private Socket TCPSocket;
    private OutputStream outputTCP;
    private InputStream inputTCP;
    private InetAddress serverAddress;
    private int serverPort;
    private ExecutorService agentExecutor;
    private ExecutorService taskExecutor = Executors.newCachedThreadPool();
    private int seqnum_atual;
    private List<String> receivedTasks_UUID = new ArrayList<>();
    private ReentrantLock lock;

    public nmsAgent(String servidorIP, int porta) throws IOException {
        this.serverAddress = InetAddress.getByName(servidorIP);
        this.serverPort = porta;
        this.socket = new DatagramSocket(); // Usa uma porta dinâmica para envio

        this.TCPSocket = new Socket(serverAddress, 1234);
        this.outputTCP = TCPSocket.getOutputStream();
        this.inputTCP = TCPSocket.getInputStream();
        System.out.println("[AGENT] TCP socket connected to server at " + servidorIP + ":" + porta);

        this.agentExecutor = Executors.newFixedThreadPool(2);
        this.seqnum_atual = 1;
        this.lock = new ReentrantLock();
    }

    public void sendByteArray(byte[] data) throws IOException {
        if (Byte.toUnsignedInt(data[36]) == AlertFlow.ALERT) {
            try {
                outputTCP.write(data);
                outputTCP.flush();
            } catch (IOException e) {
                System.out.println("[ERROR] Failed to send data via TCP: " + e.getMessage());
            }
        } else {
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverPort);
            socket.send(packet);
        }
    }

    public byte[] receiveByteArray() throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        return Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
    }

    private boolean sendWithRetry(byte[] data, int maxRetries) {
        int retryCount = 0;
        while (retryCount < maxRetries) {
            try {
                sendByteArray(data); // Envia os dados como byte array
                // Espera por um reconhecimento com timeout
                if (waitForAcknowledgment(data)) {
                    return true;
                }

                // Aplica backoff exponencial
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
            socket.setSoTimeout(5000); // Timeout de 5 segundos
            byte[] response = receiveByteArray(); // Recebe o ACK como byte array

            // Extrai a UUID do ACK
            String uuidAck = new String(Arrays.copyOfRange(response, 0, 36), StandardCharsets.UTF_8);

            // Extrai a UUID do PDU enviado
            String uuidPdu = new String(Arrays.copyOfRange(pdu, 0, 36), StandardCharsets.UTF_8);

            // Extrai o tipo do ACK
            int type = Byte.toUnsignedInt(response[36]);

            // Valida o reconhecimento
            return validateAcknowledgment(type, uuidAck, uuidPdu);
        } catch (SocketTimeoutException e) {
            return false;
        } finally {
            socket.setSoTimeout(0); // Reseta o timeout
        }
    }

    public boolean validateAcknowledgment(int type, String uuidAck, String uuidPdu) {
        lock.lock();
        try {
            // Valida se o tipo é ACKNOWLEDGE e se as UUIDs coincidem
            if (type == NetTask.ACKNOWLEDGE && uuidAck.equals(uuidPdu)) {
                System.out.println("[ACK RECEIVED] Reconhecimento bem-sucedido para UUID: " + uuidAck);
                return true;
            } else {
                System.out.println("[ACK ERROR] UUID não corresponde. ACK: " + uuidAck + " | PDU: " + uuidPdu);
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    public void register() {

        NetTask handler = new NetTask();
        byte[] registerPDU = handler.createRegisterPDU(1);
        String RegisterUUID = new String(Arrays.copyOfRange(registerPDU, 0, 36), StandardCharsets.UTF_8);
        System.out.println(RegisterUUID);

        boolean ackReceived = false;
        ackReceived = sendWithRetry(registerPDU, 5);
        System.out.println("[REGISTER SENT] Register PDU sent.");
        if (ackReceived) {
            System.out.println("[ACK RECEIVED] ACK received. Register successful.\n");
        } else {
            register();
        }
    }

    public void receiveTasks() {
        try {
            System.out.println("Waiting for tasks...");
            System.out.println();

            while (!Thread.currentThread().isInterrupted()) {
                byte[] defaultBuffer = receiveByteArray();
                int type = Byte.toUnsignedInt(defaultBuffer[36]);

                if (type == NetTask.END) {
                    processEndPdu(defaultBuffer);
                    break;
                }

                if (defaultBuffer.length > 0 && type == NetTask.TASK) {
                    byte[] bufferTemp = Arrays.copyOfRange(defaultBuffer, 0, 41);

                    byte[] pduUUIDBytes = Arrays.copyOfRange(bufferTemp, 0, 36);
                    String pduUUID = new String(pduUUIDBytes, StandardCharsets.UTF_8);
                    if (receivedTasks_UUID.contains(pduUUID)) {
                        return;
                    } else
                        receivedTasks_UUID.add(pduUUID);

                    int taskType = Byte.toUnsignedInt(bufferTemp[40]);
                    byte[] ackBytes = Arrays.copyOfRange(bufferTemp, bufferTemp.length - 4, bufferTemp.length);
                    int seqNum = ByteBuffer.wrap(new byte[] { 0, ackBytes[0], ackBytes[1], ackBytes[2] }).getInt();

                    int payloadLength = 0;
                    switch (taskType) {
                        case 0:
                            payloadLength = 2;
                            break;
                        case 1:
                            payloadLength = 2;
                            break;
                        case 2:
                            payloadLength = 9;
                            break;
                        case 3:
                        case 4:
                        case 5:
                            byte[] nextThreeBytes = Arrays.copyOfRange(defaultBuffer, 41, 44); // tive de aumentar pois
                                                                                               // demos mais bytes ao
                                                                                               // seq
                            byte iperfMode = nextThreeBytes[2];
                            if (iperfMode == 1) {
                                payloadLength = 3;
                            } else if (iperfMode == 0) {
                                payloadLength = 7;
                            }
                            break;
                        case 6:
                            payloadLength = 6;
                            break;
                        default:
                            payloadLength = 0;
                            break;
                    }
                    ;

                    byte[] bufferPayload = Arrays.copyOfRange(defaultBuffer, 41, 41 + payloadLength);
                    int freq = Byte.toUnsignedInt(bufferPayload[0]);
                    int threshold = Byte.toUnsignedInt(bufferPayload[1]);

                    final String interfaceName;
                    if (taskType == 6) {
                        byte[] interfaceNameBytes = Arrays.copyOfRange(bufferPayload, 2, 6);
                        interfaceName = new String(interfaceNameBytes);
                    } else
                        interfaceName = "";

                    final int iperfMode;
                    if (taskType == 3 || taskType == 4 || taskType == 5) {
                        iperfMode = Byte.toUnsignedInt(bufferPayload[2]);
                    } else
                        iperfMode = -1;

                    byte[] ipBytes = null;
                    if (iperfMode == 0) {
                        ipBytes = Arrays.copyOfRange(bufferPayload, bufferPayload.length - 4,
                                bufferPayload.length);
                    }

                    final String destIP;
                    if ((taskType == 3 || taskType == 4 || taskType == 5) && ipBytes != null) {
                        InetAddress inetAddress = InetAddress.getByAddress(ipBytes);
                        destIP = inetAddress.getHostAddress();
                    } else {
                        destIP = "0.0.0.0";
                    }

                    System.out.println("[TASK RECEIVED] Task received:");
                    System.out.println("Task_type: " + taskType + "\nUUID: " + pduUUID);
                    System.out.println("Frequency: " + freq + "\nThreshold: " + threshold + "\nSeq num: " + seqNum);
                    System.out.println();

                    NetTask handler = new NetTask();
                    byte[] ackPDU = handler.createAckPDU(seqNum + defaultBuffer.length, pduUUIDBytes);
                    seqnum_atual = seqNum + defaultBuffer.length;
                    int retries = 0;
                    while(retries < 5){
                        sendByteArray(ackPDU);
                        retries++;
                    }
                    System.out.println("[ACK SENT] Task received, sending ACK.");

                    if (freq != 0) {
                        Runnable taskRunnable = new Runnable() {
                            @Override
                            public void run() {
                                while (!Thread.currentThread().isInterrupted()) {
                                    try {
                                        NetTask handlerPDU = new NetTask();
                                        AlertFlow handlerAlerts = new AlertFlow();
                                        double taskOutput = executeTasks(taskType, freq, iperfMode, destIP,
                                                interfaceName);

                                        if (taskOutput > threshold && taskOutput != 404) {
                                            sendAlert(handlerAlerts, taskOutput, taskType, threshold);
                                        } else {
                                            sendMetrics(handlerPDU, taskOutput, taskType);
                                        }

                                        Thread.sleep(freq * 1000); // Espera pela frequência especificada
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                            }
                        };
                        taskExecutor.submit(taskRunnable);
                    } else {
                        // Tarefa única
                        taskExecutor.submit(() -> {
                            try {
                                NetTask handlerPDU = new NetTask();
                                AlertFlow handlerAlerts = new AlertFlow();
                                double taskOutput;

                                // Executa a tarefa
                                taskOutput = executeTasks(taskType, freq, iperfMode, destIP, interfaceName);

                                if (taskOutput > threshold && taskOutput != 404) {
                                    sendAlert(handlerAlerts, taskOutput, taskType, threshold);
                                } else {
                                    sendMetrics(handlerPDU, taskOutput, taskType);
                                }
                            } catch (InterruptedException e) {
                                System.err.println("[ERROR] Task execution interrupted: " + e.getMessage());
                                Thread.currentThread().interrupt();
                            }
                        });
                    }
                } else {
                    Thread.sleep(100);
                }
            }
        } catch (IOException | InterruptedException e) {
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("[INFO] Thread interrompida, encerrando...");
                return;
            }
        }
    }

    public double executeTasks(int taskType, int frequency, int iperfMode, String ip, String interfaceName)
            throws InterruptedException {
        TasksHandler execute = new TasksHandler();
        return execute.handleTasks(taskType, frequency, ip, iperfMode, interfaceName);
    }

    public void sendMetrics(NetTask handlerPDU, double taskOutput, int taskType) {
        if (taskOutput == 404) {
            return;
        }
        lock.lock();
        try {
            byte[] metricsPDU = handlerPDU.createOutput(taskOutput, taskType, seqnum_atual);
            byte[] metricsUUIDBytes = Arrays.copyOfRange(metricsPDU, 0, 36);
            String metricsUUID = new String(metricsUUIDBytes, StandardCharsets.UTF_8);
            boolean ackReceived = false;

            System.out.println("[METRICS SENT] Task Output sent.");
            System.out.println("     taskUUID: " + metricsUUID);
            System.out.println("     metrics:  " + taskOutput);
            System.out.println("     task_type:  " + taskType);
            System.out.println();
            ackReceived = sendWithRetry(metricsPDU, 5);

            if (!ackReceived) {
                System.out.println("[ACK NOT RECEIVED] ACKNOWLEDGEMENT not processed");
            }
        } finally {
            lock.unlock();
        }
    }

    public void sendAlert(AlertFlow handlerPDU, double taskOutput, int taskType, int threshold) {
        if (taskOutput == 404) {
            return;
        }

        lock.lock();
        try {
            System.out.println("seq metric: " + seqnum_atual);
            byte[] alertPDU = handlerPDU.createAlertFlowPDU(seqnum_atual, taskType, taskOutput, threshold);
            byte[] alertUUIDBytes = Arrays.copyOfRange(alertPDU, 0, 36);
            String alertUUID = new String(alertUUIDBytes, StandardCharsets.UTF_8);
            boolean ackReceived = false;

            System.out.println("[ALERTFLOW SENT] Alert sent to server.");
            System.out.println("     taskUUID: " + alertUUID);
            System.out.println("     metrics:  " + taskOutput);
            System.out.println("     threshold:  " + threshold);
            System.out.println("     task_type:  " + taskType);
            System.out.println();

            // Send the alert PDU via TCP
            try {
                outputTCP.write(alertPDU);
                outputTCP.flush();
            } catch (IOException e) {
                System.err.println("[ERROR] Failed to send alert PDU via TCP: " + e.getMessage());
                return;
            }

            // Wait for acknowledgment from the TCP stream
            while (!ackReceived) {
                try {
                    byte[] ackPDU = new byte[1024]; // Adjust buffer size as needed
                    int bytesRead = inputTCP.read(ackPDU);

                    if (bytesRead > 0) {
                        // Extract acknowledgment data
                        int typeInt = Byte.toUnsignedInt(ackPDU[36]);
                        String uuid = new String(Arrays.copyOfRange(ackPDU, 0, 36), StandardCharsets.UTF_8); // Type
                                                                                                             // byte
                        byte[] ackBytes = Arrays.copyOfRange(ackPDU, 37, 40); // ACK sequence number
                        int ackInt = ByteBuffer.wrap(new byte[] { 0, ackBytes[0], ackBytes[1], ackBytes[2] }).getInt();

                        // Validate the acknowledgment
                        if (validateAcknowledgment(typeInt, alertUUID, uuid)) {
                            ackReceived = true;
                            System.out.println("[ACK RECEIVED] Acknowledgment received successfully.");
                        } else {
                            System.out.println("[ACK FAILED] Received invalid ACK. Retrying...");
                            outputTCP.write(alertPDU);
                            outputTCP.flush();
                        }
                    } else {
                        System.out.println("[ERROR] No data received. Retrying...");
                    }
                } catch (IOException e) {
                    System.err.println("[ERROR] Failed to receive acknowledgment via TCP: " + e.getMessage());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void processEndPdu(byte[] endPDU) {
        try {
            int type = Byte.toUnsignedInt(endPDU[36]); // Tipo do PDU
            if (endPDU.length > 0 && type == NetTask.END) {
                byte[] UUID = Arrays.copyOfRange(endPDU, 0, 36);
                String pduUUID = new String(Arrays.copyOfRange(endPDU, 0, 36), StandardCharsets.UTF_8);

                System.out.println("[END CONNECTION] Sending end of connection message");
                System.out.println("     taskUUID: " + pduUUID);
                System.out.println("     type: " + type);
                System.out.println();

                NetTask handler = new NetTask();
                byte[] ackPDU = handler.createAckPDU(seqnum_atual + endPDU.length, UUID);

                // Enviar o ACK
                int retries = 0;
                while (retries < 2) {
                    sendByteArray(ackPDU);
                    retries++;
                }
                System.out.println("[ACK SENT] ACK enviado para o servidor.");
                closeConnection();
            }
        } catch (IOException e) {
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("[INFO] Thread interrompida, encerrando...");
                return;
            } else
                System.out.println("[ERROR] Failed to process PDU and send ACK: " + e.getMessage());
        }
    }

    public void closeConnection() {
        System.out.println("[INFO] Initiating connection shutdown...");
        if (agentExecutor != null) {
            agentExecutor.shutdownNow();
        }

        // Close sockets
        if (socket != null && !socket.isClosed()) {
            socket.close();
            System.out.println("[INFO] UDP connection closed.");
        }

        if (TCPSocket != null && !TCPSocket.isClosed()) {
            try {
                TCPSocket.close();
                System.out.println("[INFO] TCP connection closed.");
            } catch (IOException e) {
                System.err.println("[ERROR] Error closing TCP socket: " + e.getMessage());
            }
        }

        // Close input and output streams
        try {
            if (outputTCP != null) {
                outputTCP.close();
            }
            if (inputTCP != null) {
                inputTCP.close();
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Error closing streams: " + e.getMessage());
        }

        // Force exit the program
        System.exit(0);
    }

    public void start() {
        this.register();
        agentExecutor.submit(this::receiveTasks);
    }

    public static void main(String[] args) {
        int portaUDP = 12345;

        try {
            if (args.length < 1) {
                System.out.println("Usage: java nmsAgent IP=<server IP>");
                return;
            }
            nmsAgent agente = new nmsAgent(args[0], portaUDP);
            agente.start();

        } catch (IOException e) {
            System.out.println("Error initializing agent: " + e.getMessage());
        }
    }
}
