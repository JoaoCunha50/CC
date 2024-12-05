package utils;

import java.util.HashMap;
import java.util.Map;

import java.net.InetAddress;

public class SeqManager {
    private static Map<InetAddress, Integer> seqMap;

    public SeqManager() {
        seqMap = new HashMap<>();
    }

    public void add(InetAddress agentID, int seq) {
        seqMap.put(agentID, seq);
    }

    public int getNextSeqNum(byte[] packet, int seqnum) {
        int nextSeqnum = 0;
        nextSeqnum = packet.length + seqnum;
        return nextSeqnum;
    }

    public int getSeqNumber(InetAddress agentID) {
        return seqMap.get(agentID);
    }
}
