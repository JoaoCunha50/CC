import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.util.Arrays;
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

        // Método para enviar um array de bytes (não está a ser usado!! mudar código para isto)
       public void sendByteArray(byte[] data) throws IOException {
        output.write(data);           // Em seguida, envia os dados
        output.flush();               // Garante que os dados sejam enviados
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

        byte[] pduUUIDBytes = Arrays.copyOfRange(registerPDU,0,36); // copia os primeiros 36 bytes do pacote de forma a obter os bytes do UUID
        String pduUUID = new String(pduUUIDBytes, StandardCharsets.UTF_8); // transforma os bytes em String usando StandardCharsets - confirmar se está correto!

        // envio do pacote 
        sendByteArray(registerPDU);

        //debuging 
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
            agente.closeConnection();
        } catch (IOException e) {
            System.out.println("Erro ao iniciar o agente: " + e.getMessage());
        }
    }
}
