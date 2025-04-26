package volterra.informatica.quintab;
// ClientHandler.java
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
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
    private String currentRoom = "lobby";

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

            // Gestione login/registrazione
            String encryptedAuth = in.readLine();
            if (encryptedAuth == null) return;

            String decryptedAuth = decryptRSA(encryptedAuth, keyPair.getPrivate());
            String[] parts = decryptedAuth.split(" ", 3);
            if (parts.length != 3) {
                out.println("ERRORE: Formato login non valido.");
                return;
            }

            String cmd = parts[0];
            username = parts[1];
            String password = parts[2];

            synchronized (this.getClass()) {
                if (cmd.equals("REGISTER")) {
                    if (checkUserExists(username)) {
                        out.println("ERRORE: Utente esistente");
                    } else if (saveUser(username, hashPassword(password))) {
                        out.println("SUCCESS: Registrazione completata");
                        authenticated = true;
                    } else {
                        out.println("ERRORE: Errore registrazione");
                    }
                } else if (cmd.equals("LOGIN")) {
                    String storedHash = getStoredHash(username);
                    if (storedHash != null && storedHash.equals(hashPassword(password))) {
                        out.println("SUCCESS: Login effettuato");
                        authenticated = true;
                    } else {
                        out.println("ERRORE: Credenziali errate");
                    }
                } else {
                    out.println("ERRORE: Comando sconosciuto");
                }
            }

            if (!authenticated) {
                socket.close();
                return;
            }

            // Ciclo chat
            Gson gson = new Gson();
            String encrypted;
            while ((encrypted = in.readLine()) != null) {
                String decrypted = decryptRSA(encrypted, keyPair.getPrivate());
                Message msg = gson.fromJson(decrypted, Message.class);

                if (msg.getText().startsWith("/join ")) {
                    String[] split = msg.getText().substring(6).split(" ", 2);
                    String roomName = split[0].trim();
                
                    if (roomName.equalsIgnoreCase("lobby")) {
                        currentRoom = "lobby";
                        out.println("✅ Sei tornato nella lobby!");
                        continue;
                    }
                
                    if (split.length < 2) {
                        out.println("❌ Devi specificare anche la password! Esempio: /join nome password");
                        continue;
                    }
                
                    String roomPassword = split[1].trim(); 
                
                    synchronized (ChatServer.roomPasswords) {
                        if (ChatServer.roomPasswords.containsKey(roomName)) {
                            if (ChatServer.roomPasswords.get(roomName).equals(roomPassword)) {
                                currentRoom = roomName;
                                out.println("✅ Accesso alla stanza '" + roomName + "' riuscito!");
                            } else {
                                out.println("❌ Password errata per la stanza '" + roomName + "'.");
                            }
                        } else {
                            ChatServer.roomPasswords.put(roomName, roomPassword);
                            currentRoom = roomName;
                            out.println("✅ Stanza '" + roomName + "' creata e accesso effettuato!");
                        }
                    }
                    continue;
                }
                

                if (msg.getText().startsWith("/pm ")) {
                    String[] split = msg.getText().substring(4).split(" ", 2);
                    if (split.length == 2) {
                        String target = split[0];
                        String privateMsg = split[1];
                        sendPrivateMessage(target, privateMsg);
                    } else {
                        out.println("❌ Usa: /pm utente messaggio");
                    }
                    continue;
                }

                if (msg.getText().equals("/stanze")) {
                    listRooms();
                    continue;
                }

                if (msg.getText().equals("/utenti")) {
                    listUsersInSameRoom();
                    continue;
                }
                
                if (msg.getText().equals("/utentionline")) {
                    listAllOnlineUsers();
                    continue;
                }
                

                System.out.println("[" + msg.getUsername() + " @" + currentRoom + "]: " + msg.getText());
                broadcastMessage(msg);
                saveMessageToFile(msg);
            }

        } catch (Exception e) {
            System.err.println("Errore client: " + e.getMessage());
        } finally {
            ChatServer.clients.remove(this);
            try {
                socket.close();
            } catch (IOException e) {}
        }
    }

    private void broadcastMessage(Message msg) {
        Gson gson = new Gson();
        String json = gson.toJson(msg);

        for (ClientHandler client : ChatServer.clients) {
            if (client != this && client.isAuthenticated() && client.getCurrentRoom().equals(this.currentRoom)) {
                client.sendMessage(json);
            }
        }
    }

    private void sendPrivateMessage(String targetUsername, String messageText) {
        for (ClientHandler client : ChatServer.clients) {
            if (client.username.equals(targetUsername) && client.isAuthenticated()) {
                Gson gson = new Gson();
                Message privateMsg = new Message(this.username + " (privato)", messageText);
                client.sendMessage(gson.toJson(privateMsg));
                out.println("✅ PM inviato a " + targetUsername);
                return;
            }
        }
        out.println("❌ Utente non trovato.");
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

    private String decryptRSA(String encrypted, PrivateKey privateKey) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(encrypted);
        Cipher decryptCipher = Cipher.getInstance("RSA");
        decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);
        return new String(decryptCipher.doFinal(bytes));
    }

    // gestione utenti login/registrazione
    private static final Path USERS_FILE = Paths.get("users.txt");

    private boolean checkUserExists(String username) {
        if (!Files.exists(USERS_FILE)) return false;
        try {
            return Files.lines(USERS_FILE)
                .anyMatch(line -> line.startsWith(username + ":"));
        } catch (IOException e) {
            return false;
        }
    }

    private boolean saveUser(String username, String hash) {
        try {
            Files.write(USERS_FILE, (username + ":" + hash + "\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return true;
        } catch (IOException e) {
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

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void sendMessage(String json) {
        out.println(json);
    }

    public String getCurrentRoom() {
        return currentRoom;
    }

    private void listRooms() {
        out.println("📜 Stanze disponibili:");
        synchronized (ChatServer.roomPasswords) {
            if (ChatServer.roomPasswords.isEmpty()) {
                out.println("- Nessuna stanza creata. Solo la lobby è disponibile.");
                return;
            }
            for (String room : ChatServer.roomPasswords.keySet()) {
                out.println("- " + room);
            }
        }
    }

    private void listUsersInSameRoom() {
        out.println("👥 Utenti nella stanza '" + currentRoom + "':");
        for (ClientHandler client : ChatServer.clients) {
            if (client.isAuthenticated() && client.getCurrentRoom().equals(this.currentRoom)) {
                out.println("- " + client.username);
            }
        }
    }
    
    private void listAllOnlineUsers() {
        out.println("🌎 Tutti gli utenti online:");
        for (ClientHandler client : ChatServer.clients) {
            if (client.isAuthenticated()) {
                out.println("- " + client.username + " (stanza: " + client.getCurrentRoom() + ")");
            }
        }
    }    
    
}
