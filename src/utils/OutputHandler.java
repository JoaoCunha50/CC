package utils;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

public class OutputHandler {

    @SuppressWarnings("unchecked")
    public static void saveMetricsToJson(String agentName, String taskUUID, double metrics, int taskType) {
        try {
            String metricsDir = "metrics";
            String filePath = metricsDir + "/metrics.json";

            // Cria o diretório, caso não exista
            File directory = new File(metricsDir);
            if (!directory.exists()) {
                directory.mkdir();
            }

            // Cria um objeto JSON para armazenar os dados
            JSONObject existingData = new JSONObject();

            // Tenta ler o conteúdo existente no arquivo
            File jsonFile = new File(filePath);
            if (jsonFile.exists()) {
                // Lê o conteúdo do arquivo JSON
                String content = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
                if (!content.isBlank()) {
                    // Parse o conteúdo como um JSONObject
                    existingData = (JSONObject) JSONValue.parse(content);
                }
            }

            // Verifica se já existe uma lista de tasks para o agente
            JSONArray taskList = (JSONArray) existingData.get(agentName);
            if (taskList == null) {
                taskList = new JSONArray(); // Se não existir, cria uma nova lista
            }

            // Cria um objeto JSON para a nova task, incluindo taskType, taskUUID e metrics
            // na ordem correta
            JSONObject task = new JSONObject();
            task.put("taskType", taskType); // Adiciona o taskType
            task.put("metrics", metrics); // Adiciona as métricas
            task.put("taskUUID", taskUUID); // Adiciona o taskUUID

            // Adiciona a nova task na lista do agente
            taskList.add(task);

            // Atualiza a lista de tasks no objeto JSON do agente
            existingData.put(agentName, taskList);

            // Escreve o JSON atualizado no arquivo
            try (FileWriter writer = new FileWriter(filePath)) {
                writer.write(existingData.toJSONString());
            }

        } catch (Exception e) {
            System.err.println("Erro ao guardar as métricas no JSON: " + e.getMessage());
        }
    }
}
