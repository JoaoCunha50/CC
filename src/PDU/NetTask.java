package PDU;

import java.io.*;
import java.net.InetAddress;

public class NetTask implements Serializable {

    public static final int REGISTER = 0;
    public static final int ACKNOWLEDGE = 1;
    public static final int TASK = 2;
    public static final int OUTPUT = 3;
    public static final int END = 4;
    private String UUID;
    private int type;
    private byte[] data;
    
    public NetTask(String UUID,int type){
        this.UUID = UUID;
        this.type = type;
    }

    public NetTask(String UUID, int type, byte[] data) {
        this.UUID = UUID;
        this.type = type;
        this.data = data;
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

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}