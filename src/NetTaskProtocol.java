import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.time.*;

public class NetTaskProtocol {
    private Socket socket;
    private byte[] sender_buffer;
    private byte[] receiver_buffer;
    private byte[] retrasnmission_buffer;
    private int sshthresh;

    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    public byte[] getSender_buffer() {
        return sender_buffer;
    }

    public void setSender_buffer(byte[] sender_buffer) {
        this.sender_buffer = sender_buffer;
    }

    public byte[] getReceiver_buffer() {
        return receiver_buffer;
    }

    public void setReceiver_buffer(byte[] receiver_buffer) {
        this.receiver_buffer = receiver_buffer;
    }

    public byte[] getRetrasnmission_buffer() {
        return retrasnmission_buffer;
    }

    public void setRetrasnmission_buffer(byte[] retrasnmission_buffer) {
        this.retrasnmission_buffer = retrasnmission_buffer;
    }

    public int getSshthresh() {
        return sshthresh;
    }

    public void setSshthresh(int sshthresh) {
        this.sshthresh = sshthresh;
    }

    
}