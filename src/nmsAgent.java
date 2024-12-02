import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
    private int seqnum_atual;
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

    public boolean waitForAck(int type, int ackValue, byte[] pdu) {
        if (type == NetTask.ACKNOWLEDGE && ackValue == (seqnum_atual + pdu.length)) {
            seqnum_atual = ackValue;
            return true;
        } else
            return false;
    }

    public void register() {
        try {
            NetTask handler = new NetTask();
            byte[] registerPDU = handler.createRegisterPDU(seqnum_atual);
            sendByteArray(registerPDU);
            System.out.println("[REGISTER SENT] Register PDU sent.");

            try {

                byte[] response = receiveByteArray();
                if (response != null && response.length > 0) {
                    int typeInt = Byte.toUnsignedInt(response[response.length - 4]); // Ler o tipo (4º byte antes do
                                                                                     // final)

                    // Ler os 3 últimos bytes do seqNum (ACK)
                    byte[] ackBytes = Arrays.copyOfRange(response, response.length - 3, response.length);
                    int ackInt = ByteBuffer.wrap(new byte[] { 0, ackBytes[0], ackBytes[1], ackBytes[2] }).getInt();

                    if (waitForAck(typeInt, ackInt, registerPDU)) {
                        System.out.println("[ACK RECEIVED] ACK received. Register successful.\n");
                    }
                }
            } catch (SocketTimeoutException e) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                } else {
                    System.out.println("[ERROR] Failed to receive acknowledgment, re-sending ...");
                    sendByteArray(registerPDU);
                }
            }
        } catch (IOException e) {
            System.out.println("[REGISTER TIMEOUT] Register failed. Re-sending...\n" + e.getMessage());
        }
    }

    public void receiveTasks() {
        try {
            System.out.println("Waiting for tasks...");
            System.out.println();

            while (!Thread.currentThread().isInterrupted()) {
                byte[] defaultBuffer = receiveByteArray();
                int type = Byte.toUnsignedInt(defaultBuffer[36]);

                if (defaultBuffer.length > 0 && type == NetTask.TASK) {
                    byte[] bufferTemp = Arrays.copyOfRange(defaultBuffer, 0, 41); // o bufferTemp[36] é o type da
                    // mensagem!
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
                    byte[] pduUUIDBytes = Arrays.copyOfRange(bufferTemp, 0, 36);
                    String pduUUID = new String(pduUUIDBytes, StandardCharsets.UTF_8);
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
                    seqnum_atual = seqNum + defaultBuffer.length;
                    byte[] ackPDU = handler.createAckPDU(seqnum_atual);
                    int retries = 0;
                    while (retries < 3) {
                        sendByteArray(ackPDU);
                        if (retries == 0) {
                            System.out.println("[ACK SENT] Task received, sending ACK.");
                        }
                        retries++;
                    }
                    if (freq != 0) {
                        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

                        Runnable task = () -> {
                            try {
                                NetTask handlerPDU = new NetTask();
                                AlertFlow handlerAlerts = new AlertFlow();
                                double taskOutput = -1;

                                // Executa a tarefa
                                taskOutput = executeTasks(taskType, freq, iperfMode, destIP, interfaceName);

                                if (taskOutput > threshold && taskOutput != 404) {
                                    sendAlert(handlerAlerts, taskOutput, taskType, threshold, seqnum_atual);
                                } else {
                                    sendMetrics(handlerPDU, taskOutput, taskType);
                                }
                            } catch (InterruptedException e) {
                                System.err.println("[ERROR] Task execution was interrupted: " + e.getMessage());
                                Thread.currentThread().interrupt(); // Restaura o estado de interrupção da thread
                            }
                        };
                        // Agenda a tarefa com frequência definida
                        scheduler.scheduleAtFixedRate(task, 0, freq, TimeUnit.SECONDS);
                    } else {
                        NetTask handlerPDU = new NetTask();
                        AlertFlow handlerAlerts = new AlertFlow();
                        double taskOutput = -1;

                        // Executa a tarefa
                        taskOutput = executeTasks(taskType, freq, iperfMode, destIP, interfaceName);

                        if (taskOutput > threshold && taskOutput != 404) {
                            sendAlert(handlerAlerts, taskOutput, taskType, threshold, seqnum_atual);
                        } else {
                            sendMetrics(handlerPDU, taskOutput, taskType);
                        }
                    }
                } else if (type == NetTask.END) {
                    processEndPdu(defaultBuffer);
                } else {
                    Thread.sleep(100);
                }
            }
        } catch (IOException | InterruptedException e) {
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("[INFO] Thread interrompida, encerrando...");
                return;
            } else
                System.out.println("Error receiving tasks: " + e.getMessage());

        }
    }

    public double executeTasks(int taskType, int frequency, int iperfMode, String ip, String interfaceName)
            throws InterruptedException {
        TasksHandler execute = new TasksHandler();
        return execute.handleTasks(taskType, frequency, ip, iperfMode, interfaceName);
    }

    public void sendMetrics(NetTask handlerPDU, double taskOutput, int taskType) {
        try {
            if (taskOutput == 404) {
                return;
            }
            byte[] metricsPDU = handlerPDU.createOutput(taskOutput, taskType);
            byte[] metricsUUIDBytes = Arrays.copyOfRange(metricsPDU, 0, 36);
            String metricsUUID = new String(metricsUUIDBytes, StandardCharsets.UTF_8);
            boolean ackReceived = false;
            int maxRetries = 3; // Número máximo de tentativas

            System.out.println("[METRICS SENT] Task Output sent.");
            System.out.println("     taskUUID: " + metricsUUID);
            System.out.println("     metrics:  " + taskOutput);
            System.out.println("     task_type:  " + taskType);
            System.out.println();
            sendByteArray(metricsPDU);

            while (!ackReceived) {
                try {
                    byte[] ackPDU = receiveByteArray();
                    if (ackPDU != null && ackPDU.length > 0) {
                        int typeInt = Byte.toUnsignedInt(ackPDU[36]);
                        byte[] ackBytes = Arrays.copyOfRange(ackPDU, 37, 40);
                        int ackInt = ByteBuffer.wrap(new byte[] { 0, ackBytes[0], ackBytes[1], ackBytes[2] }).getInt();

                        if (waitForAck(typeInt, ackInt, metricsPDU)) {
                            ackReceived = true;
                            System.out.println("[ACK RECEIVED] Acknowledgment received successfully.");
                        } else {
                            System.out.println("[ACK FAILED] Received invalid ACK. Retrying...");
                        }
                    } else {
                        Thread.sleep(100);
                    }
                } catch (SocketTimeoutException | InterruptedException e) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    } else {
                        System.out.println("[ERROR] Failed to receive acknowledgment, re-sending ...");
                        sendByteArray(metricsPDU);
                    }
                }
            }

        } catch (IOException e) {
            System.out.println("[ERROR] Task execution was interrupted: " + e.getMessage());
        }
    }

    public void sendAlert(AlertFlow handlerPDU, double taskOutput, int taskType, int treshhold, int seq) {
        try {
            if (taskOutput == 404) {
                return;
            }
            byte[] alertPDU = handlerPDU.createAlertFlowPDU(seq, taskType, taskOutput, treshhold);
            byte[] alertUUIDBytes = Arrays.copyOfRange(alertPDU, 0, 36);
            String alertUUID = new String(alertUUIDBytes, StandardCharsets.UTF_8);
            boolean ackReceived = false;
            int maxRetries = 3; // Número máximo de tentativas

            sendByteArray(alertPDU);
            System.out.println("[ALERTFLOW SENT] Alert sent to server.");
            System.out.println("     taskUUID: " + alertUUID);
            System.out.println("     metrics:  " + taskOutput);
            System.out.println("     treshhold:  " + treshhold);
            System.out.println("     task_type:  " + taskType);
            System.out.println();

            while (!ackReceived) {
                try {
                    byte[] ackBuffer = new byte[1024];
                    int bytesRead = inputTCP.read(ackBuffer);
                    int retries = 0;
                    if (bytesRead > 0 && retries < 3) {
                        byte[] ackPDU = Arrays.copyOfRange(ackBuffer, 0, bytesRead);
                        int typeInt = Byte.toUnsignedInt(ackPDU[36]);
                        byte[] ackBytes = Arrays.copyOfRange(ackPDU, 37, 40);
                        int ackInt = ByteBuffer.wrap(new byte[] { 0, ackBytes[0], ackBytes[1], ackBytes[2] }).getInt();

                        if (waitForAck(typeInt, ackInt, alertPDU)) {
                            ackReceived = true;
                            System.out.println("[ACK RECEIVED] Acknowledgment received successfully.");
                        } else {
                            System.out.println("[ACK FAILED] Received invalid ACK. Retrying...");
                        }
                    } else {
                        System.out.println("[ACK TIMEOUT] No acknowledgment received. Retrying...");
                        Thread.sleep(100);
                        retries++;
                    }
                } catch (SocketTimeoutException | InterruptedException e) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    } else {
                        System.out.println("[ERROR] Failed to receive acknowledgment, re-sending ...");
                        sendByteArray(alertPDU);
                    }
                }
            }

        } catch (IOException e) {
            System.out.println("[ERROR] Task execution was interrupted: " + e.getMessage());
        }
    }

    public void processEndPdu(byte[] endPDU) {
        try {
            int type = Byte.toUnsignedInt(endPDU[36]); // Tipo do PDU
            if (endPDU.length > 0 && type == NetTask.END) {

                String pduUUID = new String(Arrays.copyOfRange(endPDU, 0, 36), StandardCharsets.UTF_8);

                System.out.println("[END CONNECTION] Sending end of connection message");
                System.out.println("     taskUUID: " + pduUUID);
                System.out.println("     Sequence Number:  " + seqnum_atual);
                System.out.println();

                NetTask handler = new NetTask();
                byte[] ackPDU = handler.createAckPDU(seqnum_atual + endPDU.length);

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
