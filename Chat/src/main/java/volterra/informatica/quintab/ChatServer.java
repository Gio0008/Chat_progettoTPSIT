package volterra.informatica.quintab;
//ChatServer.java
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.security.*;
import java.util.Base64;

public class ChatServer {
    private static final int PORT = 1234;
    private static ExecutorService pool = Executors.newFixedThreadPool(50);
    public static KeyPair keyPair;

    public static void main(String[] args) {
        System.out.println("Server avviato... Generazione chiavi RSA...");

        try {
            keyPair = generateRSAKeyPair();

            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Nuovo client connesso: " + clientSocket.getInetAddress());

                    ClientHandler handler = new ClientHandler(clientSocket, keyPair);
                    pool.execute(handler);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static KeyPair generateRSAKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
