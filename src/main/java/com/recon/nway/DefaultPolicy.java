package main.java.com.recon.nway;

import java.util.List;

/**
 * Default resolution: first non-empty wins (S1 > S2 > S3 ...).
 * If chosen source is not S1, append " (Si)" to the value.
 */
public class DefaultPolicy implements ReconciliationPolicy {
    @Override
    public Model.ResolvedValue resolve(List<Model.SourceRef> candidates) {
        int winner = -1;
        String value = "";
        for (int i = 0; i < candidates.size(); i++) {
            String v = candidates.get(i).value();
            if (v != null && !v.isEmpty()) {
                winner = i + 1; // 1-based
                value = v;
                break;
            }
        }
        if (winner == -1) return new Model.ResolvedValue("", -1);
        if (winner == 1) return new Model.ResolvedValue(value, winner);
        return new Model.ResolvedValue(value + " (S" + winner + ")", winner);
    }
}
