import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import utils.*;
import PDU.NetTask;

public class nmsAgent {
    private DatagramSocket socket;
    private Socket TCPSocket;
    private InetAddress serverAddress;
    private int serverPort;
    private ExecutorService agentExecutor;
    private int seqnum_atual;
    private ReentrantLock lock;

    public nmsAgent(String servidorIP, int porta) throws IOException {
        this.serverAddress = InetAddress.getByName(servidorIP);
        this.serverPort = porta;
        this.socket = new DatagramSocket(); // Usa uma porta dinâmica para envio
        this.TCPSocket = new Socket();
        this.agentExecutor = Executors.newFixedThreadPool(2);
        this.seqnum_atual = 1;
        this.lock = new ReentrantLock();
    }

    public void sendByteArray(byte[] data) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverPort);
        socket.send(packet);
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
            System.out.println("[ACK RECEIVED] ACK received. Register successful.\n");
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

            // Receber resposta
            byte[] response = receiveByteArray();
            if (response != null && response.length > 0) {
                int typeInt = Byte.toUnsignedInt(response[response.length - 2]);
                int ackInt = Byte.toUnsignedInt(response[response.length - 1]);
                waitForAck(typeInt, ackInt, registerPDU);
            }
        } catch (IOException e) {
            System.out.println("[REGISTER TIMEOUT] Register failed. Re-sending...\n" + e.getMessage());
        }
    }

    public void receiveTasks() {
        try {
            System.out.println("Waiting for tasks...");

            while (true) {
                byte[] defaultBuffer = receiveByteArray();

                if (defaultBuffer.length > 0) {
                    byte[] bufferTemp = Arrays.copyOfRange(defaultBuffer, 0, 39);
                    int taskType = Byte.toUnsignedInt(bufferTemp[37]);
                    int seqNum = Byte.toUnsignedInt(bufferTemp[38]);

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
                            payloadLength = 9;
                            break;
                        case 4:
                            payloadLength = 5;
                            break;
                        default:
                            payloadLength = 0;
                            break;
                    }
                    ;

                    byte[] bufferPayload = Arrays.copyOfRange(defaultBuffer, 39, 39 + payloadLength);
                    byte[] pduUUIDBytes = Arrays.copyOfRange(bufferTemp, 0, 36);
                    String pduUUID = new String(pduUUIDBytes, StandardCharsets.UTF_8);
                    int freq = Byte.toUnsignedInt(bufferPayload[0]);
                    int threshold = Byte.toUnsignedInt(bufferPayload[1]);

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
                                double taskOutput = -1;

                                // Executa a tarefa
                                taskOutput = executeTasks(taskType, freq);

                                if (taskOutput > threshold) {
                                    System.out.println("[ALERTFLOW] Metric is above threshold");
                                } else {
                                    sendMetrics(handlerPDU, taskOutput);
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
                        double taskOutput = -1;

                        // Executa a tarefa
                        taskOutput = executeTasks(taskType, freq);

                        if (taskOutput > threshold) {
                            System.out.println("[ALERTFLOW] Metric is above threshold");
                        } else {
                            sendMetrics(handlerPDU, taskOutput);
                        }
                    }

                } else {
                    Thread.sleep(100);
                }
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Error receiving tasks: " + e.getMessage());
        }
    }

    public double executeTasks(int taskType, int frequency) throws InterruptedException {
        TasksHandler execute = new TasksHandler();
        return execute.handleTasks(taskType, frequency, "");
    }

    public void sendMetrics(NetTask handlerPDU, double taskOutput) {
        lock.lock();
        try {
            byte[] metricsPDU = handlerPDU.createOutput(taskOutput);
            byte[] metricsUUIDBytes = Arrays.copyOfRange(metricsPDU, 0, 36);
            String metricsUUID = new String(metricsUUIDBytes, StandardCharsets.UTF_8);
            sendByteArray(metricsPDU);
            System.out.println("[METRICS SENT] Task Output sent.");
            System.out.println("     taskUUID: " + metricsUUID);
            System.out.println("     metrics:  " + taskOutput);
            System.out.println();
        } catch (IOException e) {
            System.err.println("[ERROR] Task execution was interrupted: " + e.getMessage());
            Thread.currentThread().interrupt(); // Restaura o estado de interrupção da thread
        } finally {
            lock.unlock();
        }
    }

    public void closeConnection() {
        if (socket != null) {
            socket.close();
            System.out.println("Conexão terminada\n");
        }
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
