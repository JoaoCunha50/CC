package parser;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;

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

                    // Validar campos obrigatórios
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

                    // Campos opcionais
                    String mode = null;
                    InetAddress destIpAddress = null;

                    if (dataJson.containsKey("mode")) {
                        mode = (String) dataJson.get("mode");
                    }

                    if ("client".equals(mode) && dataJson.containsKey("destination_ip")) {
                        String destination_ip = (String) dataJson.get("destination_ip");
                        if (destination_ip != null) {
                            destIpAddress = InetAddress.getByName(destination_ip);
                        }
                    } else if ("server".equals(mode)) {
                        destIpAddress = InetAddress.getByName("0.0.0.0");
                        
                    }

                    // Debug do conteúdo processado
                    System.out.println();
                    System.out.println("===============================");
                    System.out.println("ESTES SÃO OS DADOS DOS PACOTES");
                    System.out.println("agent_id: " + agent_id);
                    System.out.println("task_type: " + task_type);
                    System.out.println("frequency: " + frequency);
                    System.out.println("alertflow_condition: " + alertflow_condition);
                    System.out.println("mode: " + mode);
                    System.out.println("destination_ip: " + (destIpAddress != null ? destIpAddress : "Nenhum IP definido"));
                    System.out.println("===============================");
                    System.out.println();

                    // Criar tarefa com NetTask
                    NetTask handler = new NetTask();
                    tasksMap.put(agent_id, handler.createTaskPDU(
                            task_type,
                            frequency,
                            alertflow_condition,
                            InetAddress.getByName("192.168.1.1"),
                            destIpAddress,
                            "",
                            mode));
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
