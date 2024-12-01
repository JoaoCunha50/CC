package PDU;

import java.io.*;
import java.nio.ByteBuffer;

import java.util.UUID;

public class AlertFlow implements Serializable {

    public static final int ALERT = 5;

    public byte[] createAlertFlowPDU(int seq, int taskType, double outputValue, int treshhold) {

        String uuid = UUID.randomUUID().toString(); // Gerar UUID como string
        int type = ALERT;

        if (taskType == 5) {
            outputValue *= 10;
        }

        byte type_byte = (byte) type;
        byte[] uuidBytes = uuid.getBytes();
        byte output_byte = (byte) outputValue;
        byte taskType_byte = (byte) taskType;
        byte treshhold_byte = (byte) treshhold;
        byte[] seq_bytes = ByteBuffer.allocate(4).putInt(seq).array(); // Alocar 4 bytes para seq (inteiro completo)

        // Criar um ByteBuffer para conter o tipo e o UUID
        ByteBuffer buffer = ByteBuffer.allocate(uuidBytes.length + 1 + 3 + 1 + 1 + 1);

        buffer.put(uuidBytes); // Coloca os bytes do UUID no buffer
        buffer.put(type_byte); // Coloca o tipo (int) no buffer
        buffer.put(seq_bytes, 1, 3);
        buffer.put(taskType_byte); // coloca o task type (int) no buffer
        buffer.put(treshhold_byte);
        buffer.put(output_byte); // colocar o output no buffer

        byte[] alertPDU = buffer.array(); // Obter o array de bytes

        return alertPDU;
    }
}