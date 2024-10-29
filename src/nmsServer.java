import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class nmsServer {
    public static void main(String[] args) {
        int port = 12345; // Porta do servidor

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Servidor iniciado e aguardando conexão na porta " + port);

            // Aguarda a conexão do cliente
            Socket clientSocket = serverSocket.accept();
            System.out.println("Cliente conectado: " + clientSocket.getInetAddress());

            // Preparando para ler dados do cliente
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            String receivedMessage;
            while ((receivedMessage = in.readLine()) != null) {
                System.out.println("Mensagem recebida do cliente: " + receivedMessage);

                // Responde ao cliente
                out.println("Recebi a tua mensagem!");

                // Quebra o loop se o cliente enviar "sair"
                if ("sair".equalsIgnoreCase(receivedMessage)) {
                    System.out.println("Cliente desconectado.");
                    break;
                }
            }

            clientSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
