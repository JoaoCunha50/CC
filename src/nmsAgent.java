import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class nmsAgent {
    public static void main(String[] args) {
        String serverAddress = "127.0.0.1"; // Endereço IP do servidor
        int port = 12345; // Porta do servidor

        try (Socket socket = new Socket(serverAddress, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Conectado ao servidor em " + serverAddress + ":" + port);

            String message;
            while (true) {
                System.out.print("Digite uma mensagem para enviar (ou 'sair' para desconectar): ");
                message = scanner.nextLine();
                out.println(message); // Envia a mensagem ao servidor

                if ("sair".equalsIgnoreCase(message)) {
                    System.out.println("Desconectando do servidor.");
                    break;
                }

                // Recebe e exibe a resposta do servidor
                String response = in.readLine();
                System.out.println("Resposta do servidor: " + response);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
