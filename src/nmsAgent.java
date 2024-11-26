import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Scanner;

import utils.TasksHandler;
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
            System.out.println("Pedido de registro enviado.");

            // Receber resposta
            byte[] response = receiveByteArray();
            byte type = response[response.length - 1];
            int typeInt = Byte.toUnsignedInt(type);

            if (typeInt == NetTask.ACKNOWLEDGE) {
                System.out.println("[ACK] ACK recebido. Registro bem-sucedido.");
            } else {
                System.out.println("Registro falhou. Tipo recebido: " + typeInt);
            }
        } catch (IOException e) {
            System.out.println("Erro ao registrar: " + e.getMessage());
        }
    }

    public void receiveTasks() {
        try {
            System.out.println("Aguardando por tarefas...");

            while (true) { // Loop infinito para ficar a espera de dados
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

                    System.out.println("Recebi uma task do tipo: " + taskType + " com UUID: " + pduUUID);
                    System.out.println("Recebi uma task c/ freq: " + freq + " e com threshold: " + threshold);


                    NetTask handlerPDU = new NetTask();
                    double taskOutput = -1;
                    taskOutput = executeTasks(taskType, freq);
                    if (taskOutput > threshold) {
                        // enviar o alertflow
                    } else {
                        handlerPDU.createOutput();
                    }

                } else {
                    Thread.sleep(100);
                }
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Erro ao receber tarefas: " + e.getMessage());
        }
    }

    public double executeTasks(int taskType, int frequency) throws InterruptedException {
        TasksHandler execute = new TasksHandler();
        double output = -1;
        output = execute.handleTasks(taskType, frequency, "");
        return output;
    }

    public void closeConnection() {
        if (socket != null) {
            socket.close();
            System.out.println("Conexão terminada\n");
        }
    }

    public static void main(String[] args) {
        String servidorIP = "127.0.0.1";
        int porta = 12345;

        try {
            nmsAgent agente = new nmsAgent(servidorIP, porta);
            agente.register();
            agente.receiveTasks();

            Scanner scanner = new Scanner(System.in);
            String comando;

            System.out.println("Pressione 'q' para sair e fechar a conexão.");

            // Loop que aguarda a entrada do usuário
            while (true) {
                // Lê a entrada do usuário
                comando = scanner.nextLine();

                // Verifica se o usuário digitou 'q' para fechar a conexão
                if (comando.equalsIgnoreCase("q")) {
                    System.out.println("Fechando a conexão...");
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
