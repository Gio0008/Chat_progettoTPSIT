package volterra.informatica.quintab;

public class Message {
    private String username;
    private String text;
    private long timestamp;

    public Message(String username, String text) {
        this.username = username;
        this.text = text;
        this.timestamp = System.currentTimeMillis();
    }

    // Getter e Setter
    public String getUsername() {
        return username;
    }

    public String getText() {
        return text;
    }

    public long getTimestamp() {
        return timestamp;
    }
}

