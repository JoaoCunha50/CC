import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Scanner;

import PDU.NetTask;

public class nmsAgent {
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;

    public nmsAgent(String servidorIP, int porta) throws IOException {
        this.socket = new DatagramSocket(serverPort);
        this.serverAddress = InetAddress.getByName(servidorIP);
        this.serverPort = porta;
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
            byte type = response[response.length - 1];
            int typeInt = Byte.toUnsignedInt(type);

            if (typeInt == NetTask.ACKNOWLEDGE) {
                System.out.println("[ACK RECEIVED] ACK received. Register successfull.\n");
            }
        } catch (IOException e) {
            System.out.println("[REGISTER TIMEOUT] Register Failed. Re-sending...\n");
        }
    }

    public void receiveTasks() {
        try {
            System.out.println("Waiting for tasks...");

            while (true) { // Loop infinito para ficar a espera de dados
                NetTask handler = new NetTask();
                byte[] defaultBuffer = receiveByteArray();
                int bytesRead = defaultBuffer.length;

                if (bytesRead > 0) {

                    // Processa os dados recebidos
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
                    }

                    byte[] bufferPayload = Arrays.copyOfRange(defaultBuffer, 38, 38 + payloadLength);
                    byte[] pduUUIDBytes = Arrays.copyOfRange(bufferTemp, 0, 36);
                    String pduUUID = new String(pduUUIDBytes, StandardCharsets.UTF_8);
                    int freq = Byte.toUnsignedInt(bufferPayload[0]);
                    int threshold = Byte.toUnsignedInt(bufferPayload[1]);

                    System.out.println("[TASK RECEIVED] Task received: ");
                    System.out.println("Task_type: " + taskType + "\nUUID: " + pduUUID);
                    System.out.println("Frequency: " + freq + "\nthreshold: " + threshold);
                    System.out.println();

                    byte[] ackPDU = handler.createAckPDU();
                    sendByteArray(ackPDU);
                    System.out.println("[ACK SENT] Task Received, sending ACK");

                } else {
                    Thread.sleep(100);
                }

            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Error receiving tasks: " + e.getMessage());
        }
    }

    public void closeConnection() {
        if (socket != null) {
            socket.close();
        }
    }

    public static void main(String[] args) {
        int porta = 12345;

        try {
            nmsAgent agente = new nmsAgent(args[0], porta);
            agente.register();
            agente.receiveTasks();

            Scanner scanner = new Scanner(System.in);
            String comando;

            System.out.println("Press 'q' to close the connection.");

            // Loop que aguarda a entrada do usuário
            while (true) {
                // Lê a entrada do usuário
                comando = scanner.nextLine();

                // Verifica se o usuário digitou 'q' para fechar a conexão
                if (comando.equalsIgnoreCase("q")) {
                    System.out.println("Closing connection");
                    agente.closeConnection();
                    break; // Encerra o loop e termina o programa
                }
            }

            scanner.close(); // Fecha o scanner ao terminar o loop
        } catch (IOException e) {
            System.out.println("Erro ao iniciar o agente: " + e.getMessage());
        }
    }
}
