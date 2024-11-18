package utils;

import com.sun.management.OperatingSystemMXBean;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;

public class TasksFunctions {

    public double handleTasks(int task, int frequency, String ip) throws InterruptedException {
        double output = 0;

        switch (task) {
            case 0:
                return measureCPUusage(frequency);
            case 1:
                return measureRAMusage();
            case 2:
                return pingTask(ip);
            default:
                return output;
        }
    }

    public static double measureCPUusage(int frequency) throws InterruptedException { // tenho de tirar a frequency
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        double initialCPUload = osBean.getSystemCpuLoad();
        Thread.sleep(frequency * 1000);
        double finalCPUload = osBean.getSystemCpuLoad();

        double cpuUsage = ((initialCPUload + finalCPUload) / 2) * 100;

        // Arredonda para duas casas decimais
        cpuUsage = Math.round(cpuUsage * 100.0) / 100.0;

        // Exibe o valor formatado
        System.out.println("Uso da CPU: " + cpuUsage + "%");

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
        System.out.println("Uso da RAM: " + usedMemory + "%");

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

    private static double extractLatency(Boolean machine, String line) { // SÓ FUNCIONA PARA MÁQUINAS LINUX (!!!!)
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

    public static void main(String[] args) throws InterruptedException {
        measureCPUusage(2);
        measureRAMusage();
        pingTask("google.com");
    }

}