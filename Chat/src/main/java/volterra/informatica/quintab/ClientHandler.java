package volterra.informatica.quintab;

import java.io.*;
import java.net.*;
import java.security.*;
import javax.crypto.Cipher;
import java.util.Base64;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private KeyPair keyPair;

    public ClientHandler(Socket socket, KeyPair keyPair) {
        this.socket = socket;
        this.keyPair = keyPair;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Invia la chiave pubblica al client
            String encodedPubKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            out.println(encodedPubKey);

            // Ricevi e decifra messaggi
            String encrypted;
            while ((encrypted = in.readLine()) != null) {
                String decrypted = decryptRSA(encrypted, keyPair.getPrivate());
                System.out.println("Messaggio ricevuto (decifrato): " + decrypted);
                out.println("Ricevuto: " + decrypted);
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
}
