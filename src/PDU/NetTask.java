package PDU;

import java.io.*;
import java.net.InetAddress;
import java.util.UUID;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class NetTask implements Serializable {

    public static final int REGISTER = 0;
    public static final int ACKNOWLEDGE = 1;
    public static final int TASK = 2;
    public static final int METRICS = 3;
    public static final int END = 4;

    public byte[] createRegisterPDU(int seq) {
        String uuid = UUID.randomUUID().toString(); // Gerar UUID como string
        int type = REGISTER;

        byte type_byte = (byte) type;
        byte[] uuidBytes = uuid.getBytes();
        byte[] seq_bytes = ByteBuffer.allocate(4).putInt(seq).array(); // Alocar 4 bytes para seq (inteiro completo)

        // Criar um ByteBuffer com o tamanho correto
        ByteBuffer buffer = ByteBuffer.allocate(uuidBytes.length + 1 + 3); // Tipo (1 byte) + UUID + seq (3 bytes)
        buffer.put(uuidBytes); // Coloca os bytes do UUID no buffer
        buffer.put(type_byte); // Coloca o tipo no buffer
        buffer.put(seq_bytes, 1, 3); // Pega os últimos 3 bytes de seq e os coloca no buffer

        return buffer.array();
    }

    public byte[] createAckPDU(int ackValue, byte[] uuidBytes) {
        
        int type = ACKNOWLEDGE;

        byte type_byte = (byte) type;
        byte[] ack_bytes = ByteBuffer.allocate(4).putInt(ackValue).array(); // Alocar 3 bytes para ackValue

        // Criar um ByteBuffer para conter o tipo, UUID e ackValue
        ByteBuffer buffer = ByteBuffer.allocate(uuidBytes.length + 1 + 3); // Tipo (1 byte) + UUID + ackValue (3 bytes)
        buffer.put(uuidBytes); // Coloca os bytes do UUID no buffer
        buffer.put(type_byte); // Coloca o tipo no buffer
        buffer.put(ack_bytes, 1, 3); // Pega os últimos 3 bytes de ackValue e os coloca no buffer

        return buffer.array();
    }

    public byte[] createTaskPDU(int taskType, int freq, int threshold, InetAddress source, InetAddress destination,
            String interfaceName, String mode) {
        switch (taskType) {
            case 0: // CPU Task
                return createCPUtask(freq, threshold);
            case 1: // RAM Task
                return createRAMtask(freq, threshold);
            case 2: // Latency Task
                return createLatencyTask(freq, threshold, source, destination);
            case 3: // Throughput Task
                return createBandwidthTask(freq, threshold, destination, mode);
            case 4: // Interface Task
                return createJitterTask(freq, threshold, destination, mode);
            case 5:
                return createPacketLossTask(freq, threshold, destination, mode);
            case 6:
                return createInterfaceTask(freq, threshold, interfaceName);
            default:
                throw new IllegalArgumentException("Invalid task type: " + taskType);
        }
    }

    public byte[] createCPUtask(int freq, int threshold) {
        String uuid = UUID.randomUUID().toString();
        int type = TASK;
        int taskType = 0; // definido anteriormente

        byte[] uuidBytes = uuid.getBytes();
        byte type_byte = (byte) type;
        byte taskType_byte = (byte) taskType;
        byte freq_byte = (byte) freq;
        System.out.println("criando task com uuid de " + new String(uuidBytes, StandardCharsets.UTF_8));
        byte threshold_byte = (byte) threshold;

        ByteBuffer buffer = ByteBuffer.allocate(40);

        buffer.put(uuidBytes);
        buffer.put(type_byte);
        buffer.put(taskType_byte);
        buffer.put(freq_byte);
        buffer.put(threshold_byte);

        byte[] cpuTaskPDU = buffer.array();
        /*
         * String teste = new String(cpuTaskPDU,StandardCharsets.UTF_8);
         * String uuidteste = new String(uuidBytes,StandardCharsets.UTF_8);
         * System.out.println("É ISTO-> " + teste + " e tem este id -> " + uuidteste);
         */

        return cpuTaskPDU;
    }

    public byte[] createRAMtask(int freq, int threshold) {
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

    public byte[] createLatencyTask(int freq, int threshold, InetAddress source, InetAddress dest) {
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

    public byte[] createThroughputTask(int freq, int threshold, InetAddress source, InetAddress dest) {
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

    public byte[] createInterfaceTask(int freq, int threshold, String interfaceName) {
        String uuid = UUID.randomUUID().toString();
        int type = TASK;
        int taskType = 6; // definido anteriormente

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

    public byte[] createOutput(double outputValue, int taskType, int seq) {
        String uuid = UUID.randomUUID().toString(); // Gerar UUID como string
        int type = METRICS;

        if (taskType == 5) {
            outputValue *= 10;
        }

        byte type_byte = (byte) type;
        byte[] uuidBytes = uuid.getBytes();
        byte output_byte = (byte) outputValue;
        byte taskType_byte = (byte) taskType;

        byte[] seq_bytes = ByteBuffer.allocate(4).putInt(seq).array(); // Alocar 3 bytes para ackValue

        // Criar um ByteBuffer para conter o tipo e o UUID
        ByteBuffer buffer = ByteBuffer.allocate(uuidBytes.length + 1 + 3 + 1 + 1);

        buffer.put(uuidBytes); // Coloca os bytes do UUID no buffer
        buffer.put(type_byte); // Coloca o tipo (int) no buffer
        buffer.put(seq_bytes, 1, 3); // Pega os últimos 3 bytes de seq e os coloca no buffer
        buffer.put(taskType_byte); // coloca o task type (int) no buffer
        buffer.put(output_byte); // colocar o output no buffer

        byte[] ackPDU = buffer.array(); // Obter o array de bytes

        return ackPDU;
    }

    public byte[] createBandwidthTask(int freq, int threshold, InetAddress destination, String mode) {
        String uuid = UUID.randomUUID().toString();
        int type = TASK;
        int taskType = 3; // definido anteriormente

        byte[] uuidBytes = uuid.getBytes();
        byte type_byte = (byte) type;
        byte taskType_byte = (byte) taskType;
        byte freq_byte = (byte) freq;
        byte threshold_byte = (byte) threshold;

        // Determinar o tamanho do buffer
        int bufferSize = uuidBytes.length + 8; // Tamanho do UUID + 8 bytes para os outros campos (tipo, taskType, freq,
                                               // threshold)

        // Adicionar o tamanho extra dependendo do "mode"
        if ("server".equals(mode)) {
            bufferSize += 1; // Adiciona 1 byte para o 'server' mode
        } else if ("client".equals(mode)) {
            bufferSize += 5; // Adiciona 4 bytes para o endereço IP e 1 byte para o 'client' mode
        }

        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

        // Colocar o UUID, tipo, taskType, freq, e threshold no buffer
        buffer.put(uuidBytes);
        buffer.put(type_byte);
        // vai ser inserido um seq num aqui
        buffer.put(taskType_byte);
        buffer.put(freq_byte);
        buffer.put(threshold_byte);

        // Se for "server", adicionar 1 byte indicando "server"
        if ("server".equals(mode)) {
            byte server_byte = (byte) 1; // 1 para server
            buffer.put(server_byte);
            System.out.println();
        }
        // Se for "client", adicionar 4 bytes para o endereço IP de destino e 1 byte
        else if ("client".equals(mode)) {
            byte server_byte = (byte) 0; // 0 para client
            buffer.put(server_byte);

            // Coloca os 4 bytes do endereço IP de destino no buffer
            byte[] destinationIP_bytes = destination.getAddress(); // 4 bytes
            buffer.put(destinationIP_bytes);

            System.out.println();
        }

        return buffer.array();
    }

    public byte[] createJitterTask(int freq, int threshold, InetAddress destination, String mode) {
        String uuid = UUID.randomUUID().toString();
        int type = TASK;
        int taskType = 4; // definido anteriormente

        byte[] uuidBytes = uuid.getBytes();
        byte type_byte = (byte) type;
        byte taskType_byte = (byte) taskType;
        byte freq_byte = (byte) freq;
        byte threshold_byte = (byte) threshold;

        // Determinar o tamanho do buffer
        int bufferSize = uuidBytes.length + 8; // Tamanho do UUID + 8 bytes para os outros campos (tipo, taskType, freq,
                                               // threshold)

        // Adicionar o tamanho extra dependendo do "mode"
        if ("server".equals(mode)) {
            bufferSize += 1; // Adiciona 1 byte para o 'server' mode
        } else if ("client".equals(mode)) {
            bufferSize += 5; // Adiciona 4 bytes para o endereço IP e 1 byte para o 'client' mode
        }

        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

        // Colocar o UUID, tipo, taskType, freq, e threshold no buffer
        buffer.put(uuidBytes);
        buffer.put(type_byte);
        // vai ser inserido um seq num aqui
        buffer.put(taskType_byte);
        buffer.put(freq_byte);
        buffer.put(threshold_byte);

        // Se for "server", adicionar 1 byte indicando "server"
        if ("server".equals(mode)) {
            byte server_byte = (byte) 1; // 1 para server
            buffer.put(server_byte);
        }
        // Se for "client", adicionar 4 bytes para o endereço IP de destino e 1 byte
        else if ("client".equals(mode)) {
            byte server_byte = (byte) 0; // 0 para client
            buffer.put(server_byte);

            // Coloca os 4 bytes do endereço IP de destino no buffer
            byte[] destinationIP_bytes = destination.getAddress(); // 4 bytes
            buffer.put(destinationIP_bytes);

        }

        return buffer.array();
    }

    public byte[] createPacketLossTask(int freq, int threshold, InetAddress destination, String mode) {
        String uuid = UUID.randomUUID().toString();
        int type = TASK;
        int taskType = 5; // definido anteriormente

        byte[] uuidBytes = uuid.getBytes();
        byte type_byte = (byte) type;
        byte taskType_byte = (byte) taskType;
        byte freq_byte = (byte) freq;
        byte threshold_byte = (byte) threshold;

        // Determinar o tamanho do buffer
        int bufferSize = uuidBytes.length + 8; // Tamanho do UUID + 8 bytes para os outros campos (tipo, taskType, freq,
                                               // threshold)

        // Adicionar o tamanho extra dependendo do "mode"
        if ("server".equals(mode)) {
            bufferSize += 1; // Adiciona 1 byte para o 'server' mode
        } else if ("client".equals(mode)) {
            bufferSize += 5; // Adiciona 4 bytes para o endereço IP e 1 byte para o 'client' mode
        }

        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

        // Colocar o UUID, tipo, taskType, freq, e threshold no buffer
        buffer.put(uuidBytes);
        buffer.put(type_byte);
        // vai ser inserido um seq num aqui
        buffer.put(taskType_byte);
        buffer.put(freq_byte);
        buffer.put(threshold_byte);

        // Se for "server", adicionar 1 byte indicando "server"
        if ("server".equals(mode)) {
            byte server_byte = (byte) 1; // 1 para server
            buffer.put(server_byte);
            System.out.println();
        }
        // Se for "client", adicionar 4 bytes para o endereço IP de destino e 1 byte
        else if ("client".equals(mode)) {
            byte server_byte = (byte) 0; // 0 para client
            buffer.put(server_byte);

            // Coloca os 4 bytes do endereço IP de destino no buffer
            byte[] destinationIP_bytes = destination.getAddress(); // 4 bytes
            buffer.put(destinationIP_bytes);

            System.out.println();
        }

        return buffer.array();
    }

    public byte[] createEndPDU(int seq){
        String uuid = UUID.randomUUID().toString(); // Gerar UUID como string
        int type = END;

        byte type_byte = (byte) type;
        byte[] uuidBytes = uuid.getBytes();
        byte[] seq_bytes = ByteBuffer.allocate(4).putInt(seq).array(); // Alocar 3 bytes para ackValue

        // Criar um ByteBuffer para conter o tipo, UUID e ackValue
        ByteBuffer buffer = ByteBuffer.allocate(uuidBytes.length + 1 + 3); // Tipo (1 byte) + UUID + ackValue (3 bytes)
        buffer.put(uuidBytes); // Coloca os bytes do UUID no buffer
        buffer.put(type_byte); // Coloca o tipo no buffer
        buffer.put(seq_bytes, 1, 3); // Pega os últimos 3 bytes de ackValue e os coloca no buffer

        return buffer.array();
    }

}