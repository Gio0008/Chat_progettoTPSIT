package volterra.informatica.quintab;
// ClientHandler.java
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.*;
import javax.crypto.Cipher;
import java.util.Base64;
import com.google.gson.Gson; 

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private KeyPair keyPair;
    private boolean authenticated = false;
    private String username; 

    public ClientHandler(Socket socket, KeyPair keyPair) {
        this.socket = socket;
        this.keyPair = keyPair;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Invia chiave pubblica
            String encodedPubKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            out.println(encodedPubKey);

            // Gestione autenticazione
            String encryptedAuth = in.readLine();
            if (encryptedAuth == null) return;

            String decryptedAuth = decryptRSA(encryptedAuth, keyPair.getPrivate());
            String[] parts = decryptedAuth.split(" ", 3);
            if (parts.length != 3 || parts[1].contains(":") || parts[2].contains(":")) {
                out.println("ERRORE: Formato non valido. Usa solo caratteri consentiti");
                return;
            }

            String cmd = parts[0];
            username = parts[1];
            String password = parts[2];

            synchronized (this.getClass()) {
                if (cmd.equals("REGISTER")) {
                    if (checkUserExists(username)) {
                        out.println("ERRORE: Utente già esistente");
                    } else if (saveUser(username, hashPassword(password))) {
                        out.println("SUCCESS: Registrazione completata");
                        authenticated = true;
                    } else {
                        out.println("ERRORE: Registrazione fallita");
                    }
                } else if (cmd.equals("LOGIN")) {
                    String storedHash = getStoredHash(username);
                    if (storedHash == null) {
                        out.println("ERRORE: Utente non trovato");
                    } else if (storedHash.equals(hashPassword(password))) {
                        out.println("SUCCESS: Login effettuato");
                        authenticated = true;
                    } else {
                        out.println("ERRORE: Password errata");
                    }
                } else {
                    out.println("ERRORE: Comando non riconosciuto");
                }
            }

            if (!authenticated) {
                socket.close();
                return;
            }

            // Ciclo messaggi normali
            Gson gson = new Gson();
            String encrypted;
            while ((encrypted = in.readLine()) != null) {
                String decrypted = decryptRSA(encrypted, keyPair.getPrivate());
                Message msg = gson.fromJson(decrypted, Message.class);

                System.out.println("[" + msg.getUsername() + " @ " + new java.util.Date(msg.getTimestamp()) + "] dice: " + msg.getText());

                // Invia il messaggio a TUTTI i client collegati
                broadcastMessage(msg);
            }


        } catch (Exception e) {
            System.err.println("Errore connessione client: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {}
        }
    }

    private String decryptRSA(String encrypted, PrivateKey privateKey) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(encrypted);
        Cipher decryptCipher = Cipher.getInstance("RSA");
        decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);
        return new String(decryptCipher.doFinal(bytes));
    }

    // metodi utenti
    private static final Path USERS_FILE = Paths.get("users.txt");

    private boolean checkUserExists(String username) {
        if (!Files.exists(USERS_FILE)) return false;
        try {
            return Files.lines(USERS_FILE)
                .anyMatch(line -> line.startsWith(username + ":"));
        } catch (IOException e) {
            System.err.println("Errore lettura utenti: " + e.getMessage());
            return false;
        }
    }

    private boolean saveUser(String username, String hash) {
        try {
            Files.write(
                USERS_FILE, 
                (username + ":" + hash + "\n").getBytes(StandardCharsets.UTF_8), 
                StandardOpenOption.CREATE, 
                StandardOpenOption.APPEND
            );
            return true;
        } catch (IOException e) {
            System.err.println("Errore salvataggio utente: " + e.getMessage());
            return false;
        }
    }

    private String getStoredHash(String username) {
        if (!Files.exists(USERS_FILE)) return null;
        try {
            return Files.lines(USERS_FILE)
                .filter(line -> line.startsWith(username + ":"))
                .map(line -> line.split(":")[1])
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            System.err.println("Errore lettura hash: " + e.getMessage());
            return null;
        }
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveMessageToFile(Message msg) {
        try {
            Gson gson = new Gson();
            String filename = "chat_" + msg.getUsername() + ".json";
            Path path = Paths.get(filename);
    
            String jsonMessage = gson.toJson(msg) + "\n";
    
            Files.write(path, jsonMessage.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    
        } catch (IOException e) {
            System.err.println("Errore salvataggio chat: " + e.getMessage());
        }
    }

    private void broadcastMessage(Message msg) {
        Gson gson = new Gson();
        String json = gson.toJson(msg);
    
        for (ClientHandler client : ChatServer.clients) {
            if (client != this && client.isAuthenticated()) { // non rimandare a sé stesso
                client.sendMessage(json);
            }
        }
    }
    
    public boolean isAuthenticated() {
        return authenticated;
    }
    
    public void sendMessage(String json) {
        out.println(json);
    }    
    
}
