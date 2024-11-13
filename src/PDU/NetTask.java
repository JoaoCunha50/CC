package PDU;

import java.io.*;
import java.net.InetAddress;
import java.util.UUID;
import java.nio.ByteBuffer;


public class NetTask implements Serializable {

    public static final int REGISTER = 0;
    public static final int ACKNOWLEDGE = 1;
    public static final int TASK = 2;
    public static final int OUTPUT = 3;
    public static final int END = 4;

    public byte[] createRegisterPDU() {
        String uuid = UUID.randomUUID().toString(); // Gerar UUID como string
        int type = REGISTER;

        byte type_byte = (byte)type;
        byte[] uuidBytes = uuid.getBytes();
        
        // Criar um ByteBuffer para conter o tipo e o UUID
        ByteBuffer buffer = ByteBuffer.allocate(uuidBytes.length + 1); // 4 bytes para o int e o restante para o
        // UUID
        buffer.put(uuidBytes); // Coloca os bytes do UUID no buffer
        buffer.put(type_byte); // Coloca o tipo (int) no buffer
        
        byte[] registerPDU = buffer.array(); // Obter o array de bytes

        return registerPDU;
    }

    public byte[] createAckPDU() {
        String uuid = UUID.randomUUID().toString(); // Gerar UUID como string
        int type = ACKNOWLEDGE;

        byte type_byte = (byte) type;
        byte[] uuidBytes = uuid.getBytes();

        // Criar um ByteBuffer para conter o tipo e o UUID
        ByteBuffer buffer = ByteBuffer.allocate(uuidBytes.length + 1); // 4 bytes para o int e o restante para o
                                                                       // UUID
        buffer.put(uuidBytes); // Coloca os bytes do UUID no buffer
        buffer.put(type_byte); // Coloca o tipo (int) no buffer

        byte[] ackPDU = buffer.array(); // Obter o array de bytes

        return ackPDU;
    }

    public byte[] createTaskPDU(int taskType,int freq,int threshold, InetAddress source,InetAddress destination, String interfaceName){
        byte[] taskPDU = null;
        switch(taskType){
            case 0: // CPU Task
                taskPDU = createCPUtask(freq,threshold);
                break;
            case 1: // RAM Task
                taskPDU = createRAMtask(freq,threshold);
                break;
            case 2: // Latencia Task
                taskPDU = createLatencyTask(freq,threshold,source,destination);
                break;
            case 3: // Throughput Task
                taskPDU = createThroughputTask(freq,threshold,source,destination);
                break;
            case 4: // Interface Task
                taskPDU = createInterfaceTask(freq,threshold,interfaceName);
                break;
        }
        return taskPDU;
    }

    public byte[] createCPUtask(int freq, int threshold){
        String uuid = UUID.randomUUID().toString();
        int type = TASK;
        int taskType = 0; // definido anteriormente

        byte[] uuidBytes = uuid.getBytes();
        byte type_byte = (byte) type;
        byte taskType_byte = (byte) taskType;
        byte freq_byte = (byte) freq;
        byte threshold_byte = (byte) threshold;

        ByteBuffer buffer = ByteBuffer.allocate(uuidBytes.length + 4);

        buffer.put(uuidBytes);
        buffer.put(type_byte);
        buffer.put(taskType_byte);
        buffer.put(freq_byte);
        buffer.put(threshold_byte);

        byte[] cpuTaskPDU = buffer.array();

        return cpuTaskPDU;
    }

    public byte[] createRAMtask(int freq, int threshold){
        String uuid = UUID.randomUUID().toString();
        int type = TASK;
        int taskType = 1; // definido anteriormente

        byte[] uuidBytes = uuid.getBytes();
        byte type_byte = (byte) type;
        byte taskType_byte = (byte) taskType;
        byte freq_byte = (byte) freq;
        byte threshold_byte = (byte) threshold;

        ByteBuffer buffer = ByteBuffer.allocate(uuidBytes.length + 4);

        buffer.put(uuidBytes);
        buffer.put(type_byte);
        buffer.put(taskType_byte);
        buffer.put(freq_byte);
        buffer.put(threshold_byte);

        byte[] ramTaskPDU = buffer.array();

        return ramTaskPDU;
    }

    public byte[] createLatencyTask(int freq, int threshold,InetAddress source, InetAddress dest){
        String uuid = UUID.randomUUID().toString();
        int type = TASK;
        int taskType = 2; // definido anteriormente

        byte[] uuidBytes = uuid.getBytes();
        byte type_byte = (byte) type;
        byte taskType_byte = (byte) taskType;
        byte freq_byte = (byte) freq;
        byte threshold_byte = (byte) threshold;
        byte[] sourceBytes = source.getAddress();
        byte[] destBytes = dest.getAddress();


        ByteBuffer buffer = ByteBuffer.allocate(uuidBytes.length + 12);

        buffer.put(uuidBytes);
        buffer.put(type_byte);
        buffer.put(taskType_byte);
        buffer.put(freq_byte);
        buffer.put(threshold_byte);
        buffer.put(sourceBytes);
        buffer.put(destBytes);

        byte[] latencyTaskPDU = buffer.array();

        return latencyTaskPDU;
    }

    public byte[] createThroughputTask(int freq, int threshold,InetAddress source, InetAddress dest){
        String uuid = UUID.randomUUID().toString();
        int type = TASK;
        int taskType = 3; // definido anteriormente

        byte[] uuidBytes = uuid.getBytes();
        byte type_byte = (byte) type;
        byte taskType_byte = (byte) taskType;
        byte freq_byte = (byte) freq;
        byte threshold_byte = (byte) threshold;
        byte[] sourceBytes = source.getAddress();
        byte[] destBytes = dest.getAddress();


        ByteBuffer buffer = ByteBuffer.allocate(uuidBytes.length + 12);

        buffer.put(uuidBytes);
        buffer.put(type_byte);
        buffer.put(taskType_byte);
        buffer.put(freq_byte);
        buffer.put(threshold_byte);
        buffer.put(sourceBytes);
        buffer.put(destBytes);

        byte[] latencyTaskPDU = buffer.array();

        return latencyTaskPDU;
    }

    public byte[] createInterfaceTask(int freq, int threshold,String interfaceName){
        String uuid = UUID.randomUUID().toString();
        int type = TASK;
        int taskType = 4; // definido anteriormente

        byte[] uuidBytes = uuid.getBytes();
        byte type_byte = (byte) type;
        byte taskType_byte = (byte) taskType;
        byte freq_byte = (byte) freq;
        byte threshold_byte = (byte) threshold;
        byte[] interfaceBytes = interfaceName.getBytes();


        ByteBuffer buffer = ByteBuffer.allocate(uuidBytes.length + 8);

        buffer.put(uuidBytes);
        buffer.put(type_byte);
        buffer.put(taskType_byte);
        buffer.put(freq_byte);
        buffer.put(threshold_byte);
        buffer.put(interfaceBytes);

        byte[] interfaceTaskPDU = buffer.array();

        return interfaceTaskPDU;
    }
}