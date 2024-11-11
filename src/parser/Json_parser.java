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

    public HashMap<Integer, NetTask> tasks_parser() throws IOException, ParseException {
        JSONParser parser = new JSONParser();
        FileReader reader = new FileReader(this.file_path);

        // Criando o HashMap para armazenar as tarefas
        HashMap<Integer, NetTask> tasksMap = new HashMap<>();

        try {
            JSONArray jsonArray = (JSONArray) parser.parse(reader);

            // Para cada tarefa no array, cria um objeto NetTask
            for (Object obj : jsonArray) {
                JSONObject taskJson = (JSONObject) obj;

                // Extraindo os dados de cada tarefa
                String UUID = java.util.UUID.randomUUID().toString();
                int agent_id = ((Long)taskJson.get("agent_id")).intValue();
                int type = ((Long) taskJson.get("type")).intValue();
                byte[] data = ((String) taskJson.get("data")).getBytes(); // Convertendo a string em bytes

                NetTask task = new NetTask(UUID, type, data);

                // Adicionando a tarefa ao HashMap usando UUID como chave
                tasksMap.put(agent_id, task);
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
