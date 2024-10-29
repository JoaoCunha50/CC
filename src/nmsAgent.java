import java.io.*;
import java.net.*;

public class nmsAgent {
    public static void main(String[] args) {
        String host = "localhost";
        int port = 5000;

        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Connected to server on port " + port);
            System.out.println("Type messages to send to the server. Type 'exit' to quit.");

            String message;
            while (true) {
                System.out.print("Enter message: ");
                message = consoleInput.readLine();
                if (message.equalsIgnoreCase("exit")) {
                    break;
                }

                // Send message to server
                out.println(message);

                // Read response from server
                String response = in.readLine();
                System.out.println("Server response: " + response);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}