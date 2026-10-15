import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatServer {
    private static final int PORT = 12345;
    private static final List<PrintWriter> clientWriters = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        System.out.println(">>> Together ChatServer starting on port " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { serverSocket.close(); } catch (IOException ignored) {}
            }));

            while (true) {
                Socket clientSocket = serverSocket.accept();
                Thread t = new Thread(new ClientHandler(clientSocket));
                t.setDaemon(true);
                t.start();
            }
        } catch (IOException e) {
            System.out.println(">>> ChatServer stopped.");
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            ) {
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                clientWriters.add(out);

                String message;
                while ((message = in.readLine()) != null) {
                    for (PrintWriter writer : clientWriters) {
                        writer.println(message);
                    }
                }
            } catch (IOException ignored) {
            } finally {
                if (out != null) clientWriters.remove(out);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }
}