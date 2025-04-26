package volterra.informatica.quintab;
// Message.java
public class Message {
    private String username;
    private String text;
    private long timestamp;

    public Message(String username, String text) {
        this.username = username;
        this.text = text;
        this.timestamp = System.currentTimeMillis();
    }

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
