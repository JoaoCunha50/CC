import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class NetTask implements Serializable
{
    private static final byte[] TOKEN = ".@.".getBytes();
    private static final byte[] ENDTOKEN = "!!".getBytes();
    public static final int ACKNOWLEDGE = 0;
    private String UUID;
    private InetAddress senderNode;
    private InetAddress destinationNode;
    private int type;
    private int offset;
    private byte[] data;
    
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
}