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
        FileReader reader = new FileReader(this.file_path);

        // Criando o HashMap para armazenar as tarefas
        HashMap<Integer, byte[]> tasksMap = new HashMap<>();

        try {
            JSONArray jsonArray = (JSONArray) parser.parse(reader);

            // Para cada tarefa no array, cria um objeto NetTask
            for (Object obj : jsonArray) {
                JSONObject taskJson = (JSONObject) obj;

                // Extrair os dados da tarefa
                // Primeiro extraimos os dados "exteriores"
                int agent_id = ((Long) taskJson.get("agent_id")).intValue();
                int type = ((Long) taskJson.get("type")).intValue();

                // Extrair os dados do tipo "data"
                JSONObject dataJson = (JSONObject) taskJson.get("data");
                int task_type = ((Long) dataJson.get("task_type")).intValue();
                int frequency = ((Long) dataJson.get("frequency")).intValue();

                // Extraindo a lista de `devices`
                JSONArray devicesArray = (JSONArray) dataJson.get("devices");
                for (Object deviceObj : devicesArray) {
                    JSONObject deviceJson = (JSONObject) deviceObj;

                    // Extraindo `device_metrics`
                    JSONObject deviceMetricsJson = (JSONObject) deviceJson.get("device_metrics");
                    boolean cpu_usage = (Boolean) deviceMetricsJson.get("cpu_usage");
                    boolean ram_usage = (Boolean) deviceMetricsJson.get("ram_usage");

                    // Extraindo a lista `interface_stats`
                    JSONArray interfaceStatsArray = (JSONArray) deviceMetricsJson.get("interface_stats");
                    List<String> interfaceStats = new ArrayList<>();
                    for (Object interfaceObj : interfaceStatsArray) {
                        interfaceStats.add((String) interfaceObj);
                    }

                    // Extraindo `alertflow_conditions`
                    JSONObject alertflowConditionsJson = (JSONObject) deviceJson.get("alertflow_conditions");
                    int cpu_usage_thresholdValue = ((Long) alertflowConditionsJson.get("cpu_usage")).intValue();
                    int ram_usage_threshold = ((Long) alertflowConditionsJson.get("ram_usage")).intValue();
                    int interface_stats_threshold = ((Long) alertflowConditionsJson.get("interface_stats")).intValue();
                    int packet_loss = ((Long) alertflowConditionsJson.get("packet_loss")).intValue();
                    int jitter = ((Long) alertflowConditionsJson.get("jitter")).intValue();
                }


                // CONSTRUÇÃO DA TASK A ENVIAR
                NetTask handler = new NetTask();
                // Adicionando a tarefa ao HashMap usando agentID como chave
                tasksMap.put(agent_id, handler.createTaskPDU(task_type, frequency, 90, InetAddress.getByName("192.168.1.1"), InetAddress.getByName("192.168.1.1"), null));
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
