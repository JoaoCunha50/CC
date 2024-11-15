import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.*;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Scanner;
import java.nio.charset.StandardCharsets;

import PDU.NetTask;

public class nmsAgent {
    private Socket socket;
    private DataOutputStream output;
    private DataInputStream input;

    public nmsAgent(String servidorIP, int porta) throws IOException {
        this.socket = new Socket(servidorIP, porta);
        this.output = new DataOutputStream(socket.getOutputStream());
        this.input = new DataInputStream(socket.getInputStream());
    }

    // Método para enviar um array de bytes (não está a ser usado!! mudar código
    // para isto)
    public void sendByteArray(byte[] data) throws IOException {
        output.write(data); // Em seguida, envia os dados
        output.flush(); // Garante que os dados sejam enviados
    }

    // Método para receber um array de bytes
    public byte[] receiveByteArray() throws IOException {
        int length = input.readInt(); // Lê o comprimento dos dados
        byte[] buffer = new byte[length];

        // Lê os dados no buffer
        input.readFully(buffer); // readFully garante que todos os bytes sejam lidos

        return buffer;
    }

    public void register() {
        try {
            NetTask handler = new NetTask(); // criado o handler que vai criar os nossos pacotes para enviar
            byte[] registerPDU = handler.createRegisterPDU(); // cria um pacote do tipo REGISTER

            byte[] pduUUIDBytes = Arrays.copyOfRange(registerPDU, 0, 36); // copia os primeiros 36 bytes do pacote de
                                                                          // forma a obter os bytes do UUID
            String pduUUID = new String(pduUUIDBytes, StandardCharsets.UTF_8); // transforma os bytes em String usando
                                                                               // StandardCharsets - confirmar se está
                                                                               // correto!

            // envio do pacote
            sendByteArray(registerPDU);

            // debuging
            System.out.println("Pedido de registro enviado com UUID: " + pduUUID);

            // Receber resposta do servidor
            byte[] buffer = new byte[37];
            input.read(buffer);

            byte type = buffer[36]; // obtém o byte correspondente ao tipo
            int typeInt = Byte.toUnsignedInt(type); // transforma o byte num tipo INT
            System.out.println("O valor de type é: " + typeInt); // debuging

            if (typeInt == NetTask.ACKNOWLEDGE) { // compara se o pacote recebido é do tipo ACK
                System.out.println(
                        "ACK recebido do servidor. Registro bem-sucedido!\nIP server : " + socket.getInetAddress());
            }

        } catch (IOException e) {
            System.out.println("Erro ao enviar pedido de registro: " + e.getMessage());
            e.printStackTrace();
        }
    }

public void receiveTasks() {
    try (DataOutputStream output = new DataOutputStream(socket.getOutputStream());
         DataInputStream input = new DataInputStream(socket.getInputStream())) {

        System.out.println("Aguardando tarefas...");
        
        while (true) { // Loop infinito para ficar esperando dados
            if (input.available() > 0) { // Verifica se há dados disponíveis no stream
                byte[] defaultBuffer = new byte[100];
                int bytesRead = input.read(defaultBuffer);
                System.out.println("Li estes bytes: " + bytesRead);

                if (bytesRead > 0) {
                    // Processa os dados recebidos
                    byte[] bufferTemp = Arrays.copyOfRange(defaultBuffer, 0, 38);

                    String bufferPayloadData = new String(bufferTemp, StandardCharsets.UTF_8);
                    int taskType = Byte.toUnsignedInt(bufferTemp[37]);
                    System.out.println(taskType + " é este tipo de task");

                    int payloadLength = 0;
                    switch (taskType) {
                        case 0: payloadLength = 2; break;
                        case 1: payloadLength = 2; break;
                        case 2: payloadLength = 9; break;
                        case 3: payloadLength = 9; break;
                        case 4: payloadLength = 5; break;
                    }

                    byte[] bufferPayload = Arrays.copyOfRange(defaultBuffer, 38, 38 + payloadLength);
                    byte[] pduUUIDBytes = Arrays.copyOfRange(bufferTemp, 0, 36);
                    String pduUUID = new String(pduUUIDBytes, StandardCharsets.UTF_8);
                    int freq = Byte.toUnsignedInt(bufferPayload[0]);
                    int threshold = Byte.toUnsignedInt(bufferPayload[1]);

                   System.out.println("Recebi uma task do tipo: " + taskType + " com UUID: " + pduUUID);
                   System.out.println("Recebi uma task c/ freq: " + freq + " e com threshold: " + threshold);


            } else {
                Thread.sleep(100); 
            }
        }
    }
    } catch (IOException | InterruptedException e) {
        System.out.println("Erro ao receber a task: " + e.getMessage());
    }
}

    public void closeConnection() {
        try {
            if (input != null)
                input.close();
            if (output != null)
                output.close();
            if (socket != null)
                socket.close();
            System.out.println("Conexão terminada\n");
        } catch (IOException e) {
            System.out.println("Erro ao fechar conexão: " + e.getMessage());
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
