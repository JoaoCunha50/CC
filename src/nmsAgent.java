import java.io.*;
import java.net.*;
import java.time.LocalTime;

public class nmsAgent {
    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;

    public nmsAgent(String servidorIP, int porta) throws IOException {
        this.socket = new Socket(servidorIP, porta);
        this.output = new ObjectOutputStream(socket.getOutputStream());
        this.input = new ObjectInputStream(socket.getInputStream());
    }

    public void register() {
        try {
            NetTask task = new NetTask(
                    java.util.UUID.randomUUID().toString(),
                    InetAddress.getLocalHost(),
                    socket.getInetAddress(),
                    1, // Tipo de tarefa 1 para registro
                    0, // Número de sequência
                    1, // Tamanho da janela
                    LocalTime.now(),
                    0, // Offset
                    "Pedido de Registro".getBytes());

            // Enviar o pedido de registro
            output.writeObject(task);
            output.flush();
            System.out.println("Pedido de registro enviado com UUID: " + task.getUUID());

            // Receber resposta do servidor
            NetTask response = (NetTask) input.readObject(); // Lê o objeto NetTask
            if (response.getType() == NetTask.ACKNOWLEDGE) {
                int agenteId = Integer.parseInt(new String(response.getData()));
                System.out.println("ACK recebido do servidor. Registro bem-sucedido! ID do agente recebido: " + agenteId);
            }

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Erro ao enviar pedido de registro: " + e.getMessage());
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
