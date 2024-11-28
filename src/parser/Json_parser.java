package parser;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import PDU.NetTask;

public class Json_parser {
    String file_path;

    public Json_parser(String file_path) {
        this.file_path = file_path;
    }

    public HashMap<Integer, byte[]> tasks_parser() throws IOException, ParseException {
        JSONParser parser = new JSONParser();
        FileReader reader = null;
        HashMap<Integer, byte[]> tasksMap = new HashMap<>();

        try {
            reader = new FileReader(this.file_path);
            JSONArray jsonArray = (JSONArray) parser.parse(reader);

            for (Object obj : jsonArray) {
                try {
                    JSONObject taskJson = (JSONObject) obj;

                    // Validar se os campos necessários estão presentes
                    if (taskJson.get("agent_id") == null || taskJson.get("data") == null) {
                        System.err.println("JSON inválido ou faltando campos obrigatórios.");
                        continue;
                    }

                    int agent_id = ((Long) taskJson.get("agent_id")).intValue();
                    JSONObject dataJson = (JSONObject) taskJson.get("data");

                    if (dataJson.get("task_type") == null || dataJson.get("frequency") == null
                            || dataJson.get("alertflow_condition") == null) {
                        System.err.println("Dados incompletos na tarefa com agent_id: " + agent_id);
                        continue;
                    }

                    int task_type = ((Long) dataJson.get("task_type")).intValue();
                    int frequency = ((Long) dataJson.get("frequency")).intValue();
                    int alertflow_condition = ((Long) dataJson.get("alertflow_condition")).intValue();

                    // Criar tarefa com NetTask
                    NetTask handler = new NetTask();
                    tasksMap.put(agent_id, handler.createTaskPDU(
                            task_type,
                            frequency,
                            alertflow_condition,
                            InetAddress.getByName("192.168.1.1"),
                            InetAddress.getByName("192.168.1.1"),
                            null));
                } catch (ClassCastException | NullPointerException e) {
                    System.err.println("Erro ao processar objeto JSON: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao abrir o arquivo JSON: " + e.getMessage());
        } catch (ParseException e) {
            System.err.println("Erro ao analisar o arquivo JSON: " + e.getMessage());
        } finally {
            if (reader != null) {
                reader.close();
            }
        }

        return tasksMap;
    }

}
