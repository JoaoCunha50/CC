package utils;

import com.sun.management.OperatingSystemMXBean;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TasksHandler {

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

    public static double measureCPUusage(int frequency) {
        try {
            if (frequency == 0) {
                // Define the command to get CPU usage
                String command = "mpstat 1 1 | awk '/all/ {print 100 - $12}'";

                // Run the command
                Process process = Runtime.getRuntime().exec(new String[] { "bash", "-c", command });
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                // Read the output
                String line;
                double cpuUsage = -1.0; // Initialize with a default value
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        cpuUsage = Double.parseDouble(line.trim());
                        System.out.println("CPU Usage: " + cpuUsage + "%");
                    }
                }

                // Wait for the process to complete
                process.waitFor();

                // If needed, you can use the cpuUsage value here
                System.out.println("Parsed CPU Usage Value: " + cpuUsage);

                return cpuUsage;
            } else {
                // Schedule periodic CPU usage measurement
                ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

                Runnable task = () -> {
                    try {
                        String command = "mpstat 1 1 | awk '/all/ {print 100 - $12}'";

                        // Run the command
                        Process process = Runtime.getRuntime().exec(new String[] { "bash", "-c", command });
                        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                        // Read the output
                        String line;
                        double cpuUsage = -1.0; // Initialize with a default value
                        while ((line = reader.readLine()) != null) {
                            if (!line.trim().isEmpty()) {
                                // Parse the output into a double
                                cpuUsage = Double.parseDouble(line.trim());
                                System.out.println("CPU Usage: " + cpuUsage + "%");
                            }
                        }

                        // Wait for the process to complete
                        process.waitFor();

                        System.out.println("Parsed CPU Usage Value: " + cpuUsage);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                };
                // Schedule the task at fixed intervals
                scheduler.scheduleAtFixedRate(task, 0, frequency, TimeUnit.SECONDS);
                return -1;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
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
        System.out.println("RAM Use: " + usedMemory + "%");

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
}