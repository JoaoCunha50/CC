package utils;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import PDU.AlertFlow;
import PDU.NetTask;

public class NetworkUtils {
    private final DatagramSocket socket;
    private final List<String> received_UUID;
    private final BlockingQueue<PacketTask> sendQueue;
    private final List<PendingPacket> pendingPackets;
    private Thread sendThread;
    private Thread retransmitThread;
    private volatile boolean running;

    public NetworkUtils(DatagramSocket socket, List<String> received_UUID) {
        this.socket = socket;
        this.received_UUID = new ArrayList<>(received_UUID);
        this.sendQueue = new LinkedBlockingQueue<>();
        this.pendingPackets = new ArrayList<>();
        this.running = true;
        startSendThread();
        startRetransmitThread();
    }

    // Classe para pacotes pendentes
    private static class PendingPacket {
        final String uuid;
        final byte[] data;
        final InetSocketAddress clientAddress;
        long timestamp;
        int retries;

        public PendingPacket(String uuid, byte[] data, InetSocketAddress clientAddress, long timestamp) {
            this.uuid = uuid;
            this.data = data;
            this.clientAddress = clientAddress;
            this.timestamp = timestamp;
            this.retries = 0;
        }
    }

    public List<String> getReceived_UUID() {
        return this.received_UUID;
    }

    public BlockingQueue<PacketTask> getSendQueue() {
        return sendQueue;
    }

    public List<PendingPacket> getPendingPackets() {
        return pendingPackets;
    }

    public void addPendingPacket(String alertUUID, byte[] alertPDU) {
        this.pendingPackets.add(new PendingPacket(alertUUID, alertPDU, null, System.currentTimeMillis()));
    }

    public void removePendingPacketByUUID(String UUID) {
        for (PendingPacket pending : new ArrayList<PendingPacket>(pendingPackets)) {
            if (pending.uuid.equals(UUID)) {
                pendingPackets.remove(pending);
            }
        }
    }

    public boolean isUUIDPending(String uuid) {
        for (PendingPacket pending : pendingPackets) {
            if (pending.uuid.equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    // Iniciar thread de envio
    private void startSendThread() {
        sendThread = new Thread(() -> {
            while (running) {
                try {
                    PacketTask packetTask = sendQueue.take();
                    String uuid = extractUUID(packetTask.getData());
                    long timestamp = System.currentTimeMillis();

                    // Adicionar à lista de pacotes pendentes
                    pendingPackets.add(
                            new PendingPacket(uuid, packetTask.getData(), packetTask.getClientAddress(), timestamp));

                    sendPacket(packetTask.getData(), packetTask.getClientAddress());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        sendThread.start();
    }

    // Iniciar thread de retransmissão
    private void startRetransmitThread() {
        retransmitThread = new Thread(() -> {
            while (running) {
                long currentTime = System.currentTimeMillis();
                for (PendingPacket packet : new ArrayList<>(pendingPackets)) {
                    if ((Byte.toUnsignedInt(packet.data[36]) != AlertFlow.ALERT)
                            && currentTime - packet.timestamp > 5000 && packet.retries < 3) {
                                
                        System.out.println("[RETRANSMIT] UUID: " + packet.uuid);
                        System.out.println();

                        sendPacket(packet.data, packet.clientAddress);
                        packet.timestamp = currentTime; // Atualizar timestamp
                        packet.retries++;
                    } else if (packet.retries >= 3) {
                        System.out.println("[LOSS] Packet did not reach is destination");
                        System.out.println("        UUID: " + packet.uuid);
                        System.out.println();
                    }
                }
                try {
                    Thread.sleep(1000); // Checar a cada 1 segundo
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        retransmitThread.start();
    }

    public void stopThreads() {
        running = false;
        sendThread.interrupt();
        retransmitThread.interrupt();
    }

    public void queuePacket(byte[] data, InetSocketAddress clientAddress) {
        sendQueue.add(new PacketTask(data, clientAddress));
    }

    public void sendPacket(byte[] data, InetSocketAddress clientAddress) {
        try {
            DatagramPacket packet = new DatagramPacket(data, data.length, clientAddress.getAddress(),
                    clientAddress.getPort());
            socket.send(packet);
        } catch (IOException e) {
            System.out.println("IOException sending packet: " + e.getMessage());
        }
    }

    public List<Object> receivePacket() throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        socket.receive(packet);

        InetAddress clientAddress = packet.getAddress();
        int clientPort = packet.getPort();
        byte[] data = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
        String uuid = extractUUID(data);

        // Verificar se é um ACK
        int type = Byte.toUnsignedInt(data[36]);
        if (type == NetTask.ACKNOWLEDGE) {
            if (isUUIDPending(uuid)) {
                removePendingPacketByUUID(uuid);
                System.out.println("[ACK RECEIVED] Acknowledgement received.");
                System.out.println("        UUID: " + uuid);
                System.out.println();
            }
        }

        return Arrays.asList(data, new InetSocketAddress(clientAddress, clientPort));
    }

    private String extractUUID(byte[] data) {
        return new String(Arrays.copyOfRange(data, 0, 36), StandardCharsets.UTF_8);
    }

    private static class PacketTask {
        private final byte[] data;
        private final InetSocketAddress clientAddress;

        public PacketTask(byte[] data, InetSocketAddress clientAddress) {
            this.data = data;
            this.clientAddress = clientAddress;
        }

        public byte[] getData() {
            return data;
        }

        public InetSocketAddress getClientAddress() {
            return clientAddress;
        }
    }
}
