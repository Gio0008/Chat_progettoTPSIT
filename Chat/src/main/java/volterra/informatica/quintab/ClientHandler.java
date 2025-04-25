package volterra.informatica.quintab;

import java.io.*;
import java.net.Socket;

class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String msg;
            while ((msg = in.readLine()) != null) {
                System.out.println("Messaggio ricevuto: " + msg);
                ChatServer.broadcast(msg, this);
            }

        } catch (IOException e) {
            System.err.println("Errore connessione client");
        } finally {
            try {
                socket.close();
            } catch (IOException e) { }
        }
    }
}