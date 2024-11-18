package utils;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

public class TasksFunctions {

    public static double measureCPUusage(int frequency) throws InterruptedException {
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
        System.out.println("Uso da CPU: " + usedMemory + "%");

        return usedMemory;
    }

    public static void main(String[] args) throws InterruptedException {
        measureCPUusage(2);
        measureRAMusage();

    }

}