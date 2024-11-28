package utils;

import com.sun.management.OperatingSystemMXBean;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;

public class TasksHandler {

    public double handleTasks(int task, int frequency, String ip) throws InterruptedException {
        double output = 0;

        switch (task) {
            case 0:
                return measureCPUusage2();
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
        System.out.println("CPU Use: " + cpuUsage + "%");

        return cpuUsage;
    }

    public static double measureCPUusage2() {
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
        System.out.println(cpuUsage);
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

    public static void main(String[] args) throws InterruptedException {
        measureCPUusage2();
        measureRAMusage();
        pingTask("google.com");
    }
}