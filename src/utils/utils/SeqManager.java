package utils;

import java.util.HashMap;
import java.util.Map;

public class SeqManager {
    private static Map<Integer, Integer> seqMap;

    public SeqManager() {
        seqMap = new HashMap<>();
    }

    public void addRegistry(int agentID, int seq) {
        seqMap.put(agentID, seq);
    }

    public void addToExistingValue(int agentID, int updatedSeq) {
        if (seqMap.containsKey(agentID)) {
            seqMap.put(agentID, updatedSeq); 
        } else {
            System.out.println("AgentID não encontrado. Para adicionar, use addRegistry primeiro.");
        }
    }

    public int getSeqNumber(int agentID) {
        return seqMap.get(agentID);
    }

    public void printSeqNumbers() {
        for (Map.Entry<Integer, Integer> entry : seqMap.entrySet()) {
            System.out.println("Key: " + entry.getKey() + " Value: " + entry.getValue());
        }
    }
}
