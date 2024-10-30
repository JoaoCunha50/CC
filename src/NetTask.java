import java.io.*;
import java.net.InetAddress;
import java.time.*;

public class NetTask implements Serializable {
    private static final byte[] TOKEN = ".@.".getBytes();
    private static final byte[] ENDTOKEN = "!!".getBytes();
    public static final int ACKNOWLEDGE = 0;
    private String UUID;
    private InetAddress senderNode;
    private InetAddress destinationNode;
    private int type;
    private int seq_num;
    private int window_size;
    private LocalTime timestamp;
    private int offset;
    private byte[] data;

    public NetTask(String UUID, InetAddress senderNode, InetAddress destinationNode, int type, int seq_num,
            int window_size, LocalTime timestamp, int offset, byte[] data) {
        this.UUID = UUID;
        this.senderNode = senderNode;
        this.destinationNode = destinationNode;
        this.type = type;
        this.seq_num = seq_num;
        this.window_size = window_size;
        this.timestamp = timestamp;
        this.offset = offset;
        this.data = data;
    }

    public static byte[] getToken() {
        return TOKEN;
    }

    public static byte[] getEndtoken() {
        return ENDTOKEN;
    }

    public static int getAcknowledge() {
        return ACKNOWLEDGE;
    }

    public String getUUID() {
        return UUID;
    }

    public void setUUID(String uUID) {
        UUID = uUID;
    }

    public InetAddress getSenderNode() {
        return senderNode;
    }

    public void setSenderNode(InetAddress senderNode) {
        this.senderNode = senderNode;
    }

    public InetAddress getDestinationNode() {
        return destinationNode;
    }

    public void setDestinationNode(InetAddress destinationNode) {
        this.destinationNode = destinationNode;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public int getSeq_num() {
        return seq_num;
    }

    public int getWindow_size() {
        return window_size;
    }

    public LocalTime getTimestamp() {
        return timestamp;
    }

    public void setSeq_num(int seq_num) {
        this.seq_num = seq_num;
    }

    public void setWindow_size(int window_size) {
        this.window_size = window_size;
    }

    public void setTimestamp(LocalTime timestamp) {
        this.timestamp = timestamp;
    }
}