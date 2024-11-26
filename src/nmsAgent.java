import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import PDU.NetTask;

public class nmsAgent {
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;
    private ExecutorService agentExecutor;

    public nmsAgent(String servidorIP, int porta) throws IOException {
        this.serverAddress = InetAddress.getByName(servidorIP);
        this.serverPort = porta;
        this.socket = new DatagramSocket(); // Usa uma porta dinâmica para envio
        this.agentExecutor = Executors.newFixedThreadPool(2);
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

    public void register() {
        try {
            NetTask handler = new NetTask();
            byte[] registerPDU = handler.createRegisterPDU();
            sendByteArray(registerPDU);
            System.out.println("[REGISTER SENT] Register PDU sent.");

            // Receber resposta
            byte[] response = receiveByteArray();
            if (response != null && response.length > 0) {
                byte type = response[response.length - 1];
                int typeInt = Byte.toUnsignedInt(type);

                if (typeInt == NetTask.ACKNOWLEDGE) {
                    System.out.println("[ACK RECEIVED] ACK received. Register successful.\n");
                }
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
                    byte[] bufferTemp = Arrays.copyOfRange(defaultBuffer, 0, 38);
                    int taskType = Byte.toUnsignedInt(bufferTemp[37]);

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

                    byte[] bufferPayload = Arrays.copyOfRange(defaultBuffer, 38, 38 + payloadLength);
                    byte[] pduUUIDBytes = Arrays.copyOfRange(bufferTemp, 0, 36);
                    String pduUUID = new String(pduUUIDBytes, StandardCharsets.UTF_8);
                    int freq = Byte.toUnsignedInt(bufferPayload[0]);
                    int threshold = Byte.toUnsignedInt(bufferPayload[1]);

                    System.out.println("[TASK RECEIVED] Task received:");
                    System.out.println("Task_type: " + taskType + "\nUUID: " + pduUUID);
                    System.out.println("Frequency: " + freq + "\nThreshold: " + threshold);
                    System.out.println();

                    NetTask handler = new NetTask();
                    byte[] ackPDU = handler.createAckPDU();
                    int retries = 0;
                    while (retries < 3) {
                        sendByteArray(ackPDU);
                        if (retries == 0) {
                            System.out.println("[ACK SENT] Task received, sending ACK.");
                            retries++;
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

    public void start() {
        this.register();
        agentExecutor.submit(this::receiveTasks);
    }

    public static void main(String[] args) {
        int porta = 12345;

        try {
            if (args.length < 1) {
                System.out.println("Usage: java nmsAgent IP=<server IP>");
                return;
            }
            nmsAgent agente = new nmsAgent(args[0], porta);
            agente.start();

        } catch (IOException e) {
            System.out.println("Error initializing agent: " + e.getMessage());
        }
    }
}
