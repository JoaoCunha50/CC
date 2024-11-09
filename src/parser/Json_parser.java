package parser;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.time.LocalTime;
import java.util.HashMap;

import PDU.NetTask;

public class Json_parser {
    String file_path;

    public Json_parser(String file_path) {
        this.file_path = file_path;
    }

    public HashMap<String, NetTask> tasks_parser() throws IOException, ParseException {
        JSONParser parser = new JSONParser();
        FileReader reader = new FileReader(this.file_path);

        // Criando o HashMap para armazenar as tarefas
        HashMap<String, NetTask> tasksMap = new HashMap<>();

        try {
            JSONArray jsonArray = (JSONArray) parser.parse(reader);

            // Para cada tarefa no array, cria um objeto NetTask
            for (Object obj : jsonArray) {
                JSONObject taskJson = (JSONObject) obj;

                // Extraindo os dados de cada tarefa
                String uuid = (String) taskJson.get("UUID");
                InetAddress senderNode = InetAddress.getByName((String) taskJson.get("senderNode"));
                InetAddress destinationNode = InetAddress.getByName((String) taskJson.get("destinationNode"));
                int type = ((Long) taskJson.get("type")).intValue();
                int seqNum = ((Long) taskJson.get("seq_num")).intValue();
                int windowSize = ((Long) taskJson.get("window_size")).intValue();
                String timestampStr = (String) taskJson.get("timestamp");

                // Convertendo o timestamp para LocalTime
                LocalTime timestamp = LocalTime.parse(timestampStr);

                int offset = ((Long) taskJson.get("offset")).intValue();
                byte[] data = ((String) taskJson.get("data")).getBytes(); // Convertendo a string em bytes

                NetTask task = new NetTask(uuid, senderNode, destinationNode, type, seqNum, windowSize, data);

                // Adicionando a tarefa ao HashMap usando UUID como chave
                tasksMap.put(uuid, task);
            }
        } catch (ParseException e) {
            e.printStackTrace();
        } finally {
            reader.close();
        }

        // Retornando o HashMap com as tarefas
        return tasksMap;
    }
}
