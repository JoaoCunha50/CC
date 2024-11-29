package utils;

import com.sun.management.OperatingSystemMXBean;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TasksHandler {

    public double handleTasks(int task, int frequency, String ip, int mode) throws InterruptedException {
        double output = 404;
        switch (task) {
            case 0:
                return measureCPUusage();
            case 1:
                return measureRAMusage();
            case 2:
                return pingTask(ip);
            case 3:
                if (mode == 1) {
                    createIperfServer();
                } else if (mode == 0) {
                    return getIperfBandwidth(ip);
                }
            case 4:
                if (mode == 1) {
                    createIperfServer();
                } else if (mode == 0) {
                    return getIperfJitter(ip);
                }
            default:
                return output;
        }
    }

    public static double measureCPUusage() {
        double cpuUsage = -1.0; // Initialize with a default value
        try {
            // Define the command to get CPU usage
            String command = "mpstat 1 1 | awk '/all/ {print 100 - $12}'";

            // Run the command
            Process process = Runtime.getRuntime().exec(new String[] { "bash", "-c", command });
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            // Read the output
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    // Parse the output into a double
                    cpuUsage = Double.parseDouble(line.trim());
                }
            }

            // Wait for the process to complete
            process.waitFor();

        } catch (Exception e) {
            e.printStackTrace();
        }
        // Return the CPU usage
        return cpuUsage;
    }

    public static double measureRAMusage() throws InterruptedException {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        // Obtem a memória total e a disponível
        long totalMemory = osBean.getTotalPhysicalMemorySize();
        long freeMemory = osBean.getFreePhysicalMemorySize();

        // Calcula a porcentagem de memória usada
        double usedMemory = ((double) (totalMemory - freeMemory) / totalMemory) * 100;
        // Arredonda para duas casas decimais
        usedMemory = Math.round(usedMemory * 100.0) / 100.0;

        return usedMemory;
    }

    public static double pingTask(String destinationIP) {
        StringBuilder output = new StringBuilder();
        double latency = -1;

        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder processBuilder;
            boolean isWindows = false;
            isWindows = os.contains("win"); // retorna TRUE caso seja uma máquina windows

            if (isWindows) {
                processBuilder = new ProcessBuilder("ping", "-n", "10", destinationIP);
            } else {
                processBuilder = new ProcessBuilder("ping", "-c", "10", destinationIP);
            }

            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());

                if (line.toLowerCase().contains("average")) { // Para máquinas windows
                    latency = extractLatency(isWindows, line);
                } else if (line.toLowerCase().contains("avg")) { // Para máquinas UNIX/LINUX
                    latency = extractLatency(isWindows, line);
                }

                process.waitFor();

            }
        } catch (Exception e) {
            System.err.println("An error occurred: " + e.getMessage());
        }
        return latency;
    }

    private static double extractLatency(Boolean machine, String line) {
        String[] parts = null;
        String avg = "";

        if (machine) { // caso seja uma máquina windows
            parts = line.split("Average = ")[1].split("ms");
            avg = parts[0];
        } else {
            parts = line.split(" = ")[1].split(" ")[0].split("/");
            avg = parts[1];
        }

        double avgValue = Double.parseDouble(avg);
        System.out.println(avgValue);

        return avgValue;
    }

    private static void createIperfServer() {
        String command = "iperf3 -s"; // Pode usar "iperf -s" ou "iperf3 -s" dependendo da versão
        try {
            // Usando o ProcessBuilder para rodar o comando no sistema operacional
            ProcessBuilder builder = new ProcessBuilder(command.split(" "));

            // Redireciona a saída do processo para null (sem output no console)
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);

            // Inicia o processo em segundo plano
            Process process = builder.start();

            System.out.println("Servidor IPERF iniciado em segundo plano!");

            // Não aguarda o processo para não bloquear a execução
            // process.waitFor(); // Remover esta linha

        } catch (IOException e) {
            System.err.println("Erro ao executar o comando: " + e.getMessage());
        }
    }

    private static double getIperfBandwidth(String serverIp) {
        String command = "iperf3 -c " + serverIp + " -t 10 -f m"; // "-f m" para exibir a largura de banda em Mbps
        double bandwidth = -1.0; // Valor inicial negativo indicando que a largura de banda não foi encontrada.

        try {
            // Usando o ProcessBuilder para rodar o comando no sistema operacional
            ProcessBuilder builder = new ProcessBuilder(command.split(" "));

            // Captura a saída do processo
            Process process = builder.start();

            // Ler a saída do processo e procurar pela linha que contém a largura de banda
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.contains("Mbits/sec")) { // A linha com a largura de banda contém "Mbits/sec"
                    // Encontrando a largura de banda na linha
                    String[] parts = line.split("\\s+");
                    String bandwidthStr = parts[parts.length - 2]; // Largura de banda geralmente está na penúltima
                                                                   // posição

                    try {
                        // Convertendo para double
                        bandwidth = Double.parseDouble(bandwidthStr);
                    } catch (NumberFormatException e) {
                        System.err.println("Erro ao converter a largura de banda: " + e.getMessage());
                    }
                    break;
                }
            }

            process.waitFor();

        } catch (IOException e) {
            System.err.println("Erro ao executar o comando: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("O processo foi interrompido: " + e.getMessage());
        }
        // System.out.println("A largura de banda é: " + bandwidth);

        return bandwidth;
    }

    private static double getIperfJitter(String serverIp) {
        try {
            // Comando para executar o iperf3
            String command = "iperf3 -c " + serverIp + " -u -t 10";

            // Criação do processo
            ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
            Process process = processBuilder.start();

            // Aguarda o término do processo
            process.waitFor();

            // Lê a saída completa do comando
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String lastRelevantLine = null; // Para armazenar a última linha relevante

            // Itera pela saída linha por linha e guarda a última linha relevante
            while ((line = reader.readLine()) != null) {
                if (line.contains("ms")) { // Busca linhas que contenham "ms"
                    lastRelevantLine = line; // Atualiza para a última linha encontrada
                }
            }

            // Verifica se encontramos uma linha relevante
            if (lastRelevantLine != null) {
                // Divide a linha para capturar o valor antes de "ms"
                String[] parts = lastRelevantLine.trim().split("\\s+"); // Divide por espaços
                for (int i = 0; i < parts.length; i++) {
                    if (parts[i].equals("ms") && i > 0) { // Encontra "ms" e pega o valor anterior
                        double jitterMs = Double.parseDouble(parts[i - 1]); // Converte o valor anterior para double
                        double jitterSeconds = jitterMs * 1000.0; // Converte milissegundos para microsegundos
                        return jitterSeconds; // Retorna o jitter em microsegundos
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        // Retorna 0.0 se nenhum jitter for encontrado
        return 0.0;
    }

}
