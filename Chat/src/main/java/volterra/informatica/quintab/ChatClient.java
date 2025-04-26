package volterra.informatica.quintab;

import java.net.*;
import java.io.*;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;
import javax.crypto.Cipher;

public class ChatClient {
    private static final String HOST = "localhost";
    private static final int PORT = 1234;

    public static void main(String[] args) {
        try (
            Socket socket = new Socket(HOST, PORT);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in))
        ) {
            System.out.println("Connesso al server!");

            // Ricezione chiave pubblica
            String pubKeyBase64 = in.readLine();
            byte[] keyBytes = Base64.getDecoder().decode(pubKeyBase64);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey serverPublicKey = keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));

            // Autenticazione
            boolean authenticated = false;
            while (!authenticated) {
                System.out.println("Scegli un'opzione:");
                System.out.println("1. Registrati");
                System.out.println("2. Login");
                String choice = keyboard.readLine();

                System.out.print("Username (no spazi): ");
                String username = keyboard.readLine().trim().replaceAll("\\s+", "");

                System.out.print("Password (no spazi): ");
                String password = keyboard.readLine().trim().replaceAll("\\s+", "");

                String command;
                if (choice.equals("1")) {
                    command = "REGISTER " + username + " " + password;
                } else if (choice.equals("2")) {
                    command = "LOGIN " + username + " " + password;
                } else {
                    System.out.println("Scelta non valida!");
                    continue;
                }

                // Invia comando cifrato
                String encrypted = encryptRSA(command, serverPublicKey);
                out.println(encrypted);

                // Ricevi risposta
                String response = in.readLine();
                System.out.println("Server: " + response);
                if (response != null && response.startsWith("SUCCESS")) {
                    authenticated = true;
                }
            }

            // Ciclo di chat
            System.out.println("Benvenuto nella chat! Scrivi i tuoi messaggi:");
            String input;
            while ((input = keyboard.readLine()) != null) {
                String encrypted = encryptRSA(input, serverPublicKey);
                out.println(encrypted);
                String response = in.readLine();
                System.out.println("Server: " + response);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String encryptRSA(String message, PublicKey publicKey) throws Exception {
        Cipher encryptCipher = Cipher.getInstance("RSA");
        encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encrypted = encryptCipher.doFinal(message.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }
}