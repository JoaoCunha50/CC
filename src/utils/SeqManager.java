package utils;

import java.util.HashMap;
import java.util.Map;

public class SeqManager {
    private static Map<String, Integer> seqMap;

    public SeqManager() {
        seqMap = new HashMap<>();
    }

    public void addRegistry(String agentID, int seq) {
        seqMap.put(agentID, seq);
    }

    public void addToExistingValue(String agentID, int updatedSeq) {
        if (seqMap.containsKey(agentID)) {
            seqMap.put(agentID, updatedSeq); 
        } else {
            System.out.println("AgentID não encontrado. Para adicionar, use addRegistry primeiro.");
        }
    }

    public int getNextSeqNum(byte[] packet, int seqnum){
        int nextSeqnum = 0;
        nextSeqnum = packet.length + seqnum;
        return nextSeqnum;
    }

    public int getSeqNumber(String agentID) {
        return seqMap.get(agentID);
    }
}
