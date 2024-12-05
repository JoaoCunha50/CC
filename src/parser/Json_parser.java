package parser;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import PDU.NetTask;

public class Json_parser {
    String file_path;

    public Json_parser(String file_path) {
        this.file_path = file_path;
    }

    public HashMap<String, List<byte[]>> tasks_parser() throws IOException, ParseException {
        JSONParser parser = new JSONParser();
        FileReader reader = null;
        HashMap<String, List<byte[]>> tasksMap = new HashMap<>(); // Mapeia agent_id -> lista de tarefas (byte[])

        try {
            reader = new FileReader(this.file_path);
            JSONArray jsonArray = (JSONArray) parser.parse(reader);

            for (Object obj : jsonArray) {
                try {
                    JSONObject agentJson = (JSONObject) obj;

                    // Validar campos obrigatórios
                    if (agentJson.get("agent_id") == null || agentJson.get("tasks") == null) {
                        System.err.println("JSON inválido ou faltando campos obrigatórios.");
                        continue;
                    }

                    String agent_id = ((String) agentJson.get("agent_id"));
                    JSONArray tasksArray = (JSONArray) agentJson.get("tasks");

                    // Inicializar lista para armazenar tarefas do agente
                    List<byte[]> agentTasks = new ArrayList<>();

                    for (Object taskObj : tasksArray) {
                        try {
                            JSONObject taskJson = (JSONObject) taskObj;

                            int task_type = ((Long) taskJson.get("task_type")).intValue();
                            int frequency = ((Long) taskJson.get("frequency")).intValue();
                            int alertflow_condition = ((Long) taskJson.get("alertflow_condition")).intValue();

                            String mode = null;
                            InetAddress destIpAddress = null;
                            String interfaceName = null;

                            if (taskJson.containsKey("mode")) {
                                mode = (String) taskJson.get("mode");
                            }

                            if (taskJson.containsKey("interfaceName")) {
                                interfaceName = (String) taskJson.get("interfaceName");
                            }

                            if ("client".equals(mode) && taskJson.containsKey("destination_ip")) {
                                String destination_ip = (String) taskJson.get("destination_ip");
                                if (destination_ip != null) {
                                    destIpAddress = InetAddress.getByName(destination_ip);
                                }
                            } else if ("server".equals(mode)) {
                                destIpAddress = InetAddress.getByName("0.0.0.0");
                            }

                            // Criar tarefa com NetTask
                            NetTask handler = new NetTask();
                            byte[] taskPDU = handler.createTaskPDU(
                                    task_type,
                                    frequency,
                                    alertflow_condition,
                                    InetAddress.getByName("192.168.1.1"),
                                    destIpAddress,
                                    interfaceName, // Passar o interfaceName para o NetTask
                                    mode);

                            // Adicionar tarefa à lista do agente
                            agentTasks.add(taskPDU);
                        } catch (ClassCastException | NullPointerException e) {
                            System.err.println(
                                    "Erro ao processar tarefa para agent_id " + agent_id + ": " + e.getMessage());
                        }
                    }

                    // Adicionar lista de tarefas ao mapa com agent_id como chave
                    tasksMap.put(agent_id, agentTasks);
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
