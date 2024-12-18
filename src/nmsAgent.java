import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    private InetSocketAddress serverSocketAddress;
    private int serverPort;
    private List<String> receivedTasks_UUID = new ArrayList<>();
    private List<Thread> ThreadList = new ArrayList<>();
    private NetworkUtils utils;
    private ReentrantLock lock;

    public nmsAgent(String servidorIP, int porta) throws IOException {
        this.serverAddress = InetAddress.getByName(servidorIP);
        this.serverPort = porta;
        this.serverSocketAddress = new InetSocketAddress(serverAddress, porta);
        this.socket = new DatagramSocket(); // Usa uma porta dinâmica para envio

        this.TCPSocket = new Socket(serverAddress, 1234);
        this.outputTCP = TCPSocket.getOutputStream();
        this.inputTCP = TCPSocket.getInputStream();

        this.utils = new NetworkUtils(socket, receivedTasks_UUID);

        this.lock = new ReentrantLock();
    }

    public void register() {
        try {
            System.out.println(
                    "[AGENT] TCP socket connected to server at " + serverAddress.toString() + ":" + serverPort);
            System.out.println();

            NetTask handler = new NetTask();
            byte[] registerPDU = handler.createRegisterPDU(1);
            String RegisterUUID = new String(Arrays.copyOfRange(registerPDU, 0, 36), StandardCharsets.UTF_8);

            System.out.println("[REGISTER SENT] Register PDU sent.");
            System.out.println("           UUID: " + RegisterUUID);
            System.out.println("           seqNum: " + 1);
            System.out.println();

            utils.getSeqManager().add(serverAddress, 1);

            utils.queuePacket(registerPDU, serverSocketAddress);

            utils.receivePacket();
            
            while (utils.isUUIDPending(RegisterUUID) != null) {
                Thread.sleep(100);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void receiveTasks() {
        try {

            while (!Thread.currentThread().isInterrupted()) {
                List<Object> data = utils.receivePacket();
                byte[] defaultBuffer = (byte[]) data.get(0);

                int type = Byte.toUnsignedInt(defaultBuffer[36]);

                Thread processTasks = new Thread(() -> {
                    try {
                        if (defaultBuffer.length > 0 && type == NetTask.TASK) {
                            byte[] bufferTemp = Arrays.copyOfRange(defaultBuffer, 0, 41);

                            byte[] pduUUIDBytes = Arrays.copyOfRange(bufferTemp, 0, 36);
                            String pduUUID = new String(pduUUIDBytes, StandardCharsets.UTF_8);

                            int taskType = Byte.toUnsignedInt(bufferTemp[40]);
                            byte[] ackBytes = Arrays.copyOfRange(bufferTemp, bufferTemp.length - 4, bufferTemp.length);
                            int seqNum = ByteBuffer.wrap(new byte[] { 0, ackBytes[0], ackBytes[1], ackBytes[2] })
                                    .getInt();

                            NetTask handler = new NetTask();
                            int seq = seqNum + defaultBuffer.length;
                            byte[] ackPDU = handler.createAckPDU(seq, pduUUIDBytes);
                            utils.getSeqManager().add(serverAddress, seqNum + defaultBuffer.length);

                            if (receivedTasks_UUID.contains(pduUUID)) {
                                utils.sendPacket(ackPDU, serverSocketAddress);
                                return;
                            } else
                                receivedTasks_UUID.add(pduUUID);

                            utils.sendPacket(ackPDU, serverSocketAddress);

                            int payloadLength = 0;
                            switch (taskType) {
                                case 0:
                                    payloadLength = 2;
                                    break;
                                case 1:
                                    payloadLength = 2;
                                    break;
                                case 2:
                                    payloadLength = 6;
                                    break;
                                case 3:
                                case 4:
                                case 5:
                                    byte[] nextThreeBytes = Arrays.copyOfRange(defaultBuffer, 41, 44);
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
                            if (iperfMode == 0 || taskType == 2) {
                                ipBytes = Arrays.copyOfRange(bufferPayload, bufferPayload.length - 4,
                                        bufferPayload.length);
                            }

                            final String destIP;
                            if ((taskType == 2 || taskType == 3 || taskType == 4 || taskType == 5) && ipBytes != null) {
                                InetAddress inetAddress = InetAddress.getByAddress(ipBytes);
                                destIP = inetAddress.getHostAddress();
                            } else {
                                destIP = "0.0.0.0";
                            }

                            lock.lock(); // Apenas para melhor entendimento dos prints !!
                            try {
                                System.out.println("[TASK RECEIVED] Task received:");
                                System.out.println("                UUID: " + pduUUID);
                                System.out.println("                seqNum: " + seqNum);
                                System.out.println("                taskType: " + taskType);
                                System.out.println("                frequency: " + freq);
                                System.out.println("                threshold: " + threshold);
                                System.out.println();

                                System.out.println("[ACK SENT] Acknowledgement sent to agent");
                                System.out.println("           UUID: " + pduUUID);
                                System.out.println("           seqNum: " + seq);
                                System.out.println();
                            } finally {
                                lock.unlock();
                            }

                            Thread taskExecutor = new Thread(() -> {
                                if (freq != 0) {
                                    while (!Thread.currentThread().isInterrupted()) {
                                        try {
                                            NetTask handlerPDU = new NetTask();
                                            AlertFlow handlerAlerts = new AlertFlow();
                                            double taskOutput = executeTasks(taskType, freq, iperfMode, destIP,
                                                    interfaceName);
                                            if (taskType != 3) {
                                                if (taskOutput > threshold && taskOutput != 404) {
                                                    sendAlert(handlerAlerts, taskOutput, taskType, threshold);
                                                } else {
                                                    sendMetrics(handlerPDU, taskOutput, taskType);
                                                }
                                            } else {
                                                if (taskOutput < threshold && taskOutput != 404) {
                                                    sendAlert(handlerAlerts, taskOutput, taskType, threshold);
                                                } else {
                                                    sendMetrics(handlerPDU, taskOutput, taskType);
                                                }
                                            }

                                            Thread.sleep(freq * 1000); // Espera pela frequência especificada
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                        }
                                    }
                                } else {
                                    try {
                                        NetTask handlerPDU = new NetTask();
                                        AlertFlow handlerAlerts = new AlertFlow();
                                        double taskOutput;

                                        // Executa a tarefa
                                        taskOutput = executeTasks(taskType, freq, iperfMode, destIP, interfaceName);

                                        if (taskType != 3) {
                                            if (taskOutput > threshold && taskOutput != 404) {
                                                sendAlert(handlerAlerts, taskOutput, taskType, threshold);
                                            } else {
                                                sendMetrics(handlerPDU, taskOutput, taskType);
                                            }
                                        } else {
                                            if (taskOutput < threshold && taskOutput != 404) {
                                                sendAlert(handlerAlerts, taskOutput, taskType, threshold);
                                            } else {
                                                sendMetrics(handlerPDU, taskOutput, taskType);
                                            }
                                        }
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                            });
                            ThreadList.add(taskExecutor);
                            taskExecutor.start();
                        } else {
                            Thread.sleep(100);
                        }
                    } catch (IOException | InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                ThreadList.add(processTasks);
                processTasks.start();

                Thread processEndPdu = new Thread(() -> {
                    if (type == NetTask.END) {
                        processEndPdu(defaultBuffer);
                    }
                });
                ThreadList.add(processEndPdu);
                processEndPdu.start();
            }
        } catch (IOException e) {
            Thread.currentThread().interrupt();
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
            int seq = utils.getSeqManager().getSeqNumber(serverAddress);
            byte[] metricsPDU = handlerPDU.createOutput(taskOutput, taskType, seq);
            byte[] metricsUUIDBytes = Arrays.copyOfRange(metricsPDU, 0, 36);
            String metricsUUID = new String(metricsUUIDBytes, StandardCharsets.UTF_8);

            System.out.println("[METRICS SENT] Task Output sent.");
            System.out.println("               taskUUID: " + metricsUUID);
            System.out.println("               seqNum:  " + seq);
            System.out.println("               metrics:  " + taskOutput);
            System.out.println("               task_type:  " + taskType);
            System.out.println();
            utils.queuePacket(metricsPDU, serverSocketAddress);
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
            int seq = utils.getSeqManager().getSeqNumber(serverAddress);
            byte[] alertPDU = handlerPDU.createAlertFlowPDU(seq, taskType, taskOutput, threshold);
            byte[] alertUUIDBytes = Arrays.copyOfRange(alertPDU, 0, 36);
            String alertUUID = new String(alertUUIDBytes, StandardCharsets.UTF_8);
            boolean ackReceived = false;

            System.out.println("[ALERTFLOW SENT] Alert sent to server.");
            System.out.println("                 taskUUID: " + alertUUID);
            System.out.println("                 seqNum:  " + seq);
            System.out.println("                 metrics:  " + taskOutput);
            System.out.println("                 threshold:  " + threshold);
            System.out.println("                 task_type:  " + taskType);
            System.out.println();

            // Send the alert PDU via TCP
            try {
                outputTCP.write(alertPDU);
                outputTCP.flush();
                utils.addPendingPacket(alertUUID, alertPDU);
            } catch (IOException e) {
                System.err.println("[ERROR] Failed to send alert PDU via TCP: " + e.getMessage());
                return;
            }

            int maxRetries = 0;

            // Wait for acknowledgment from the TCP stream
            while (!ackReceived && maxRetries < 3) {
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
                        if (typeInt == NetTask.ACKNOWLEDGE
                                && ackInt == alertPDU.length + utils.getSeqManager().getSeqNumber(serverAddress)
                                && uuid.equals(alertUUID)) {
                            utils.removePendingPacketByUUID(uuid);
                            ackReceived = true;
                            System.out.println("[ACK RECEIVED] Acknowledgement received.");
                            System.out.println("               UUID: " + uuid);
                            System.out.println("               seqNum: " + ackInt);
                            System.out.println();
                        } else {
                            outputTCP.write(alertPDU);
                            outputTCP.flush();
                            System.out.println("[RETRANSMIT] UUID: " + alertUUID);
                            System.out.println();
                            maxRetries++;
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
        int type = Byte.toUnsignedInt(endPDU[36]); // Tipo do PDU
        if (endPDU.length > 0 && type == NetTask.END) {

            String pduUUID = new String(Arrays.copyOfRange(endPDU, 0, 36), StandardCharsets.UTF_8);

            System.out.println("[END CONNECTION] Sending end of connection message");
            System.out.println("                taskUUID: " + pduUUID);
            System.out.println("                type: " + type);
            System.out.println();

            closeConnection();
        }
    }

    public void closeConnection() {
        System.out.println("[INFO] Initiating connection shutdown...");

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

        for (Thread t : ThreadList) {
            if (t.isAlive()) {
                t.interrupt();
            }
        }

        utils.stopThreads();

        // Force exit the program
        System.exit(0);
    }

    public void start() {
        this.register();
        this.receiveTasks();
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
