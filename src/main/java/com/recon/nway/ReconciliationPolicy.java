package main.java.com.recon.nway;

import java.util.List;

/** Strategy for selecting a single value with provenance from N source candidates. */
public interface ReconciliationPolicy {
    /**
     * @param candidates values from sources in fixed order S1..SN (empty string means missing)
     * @return chosen value (possibly annotated) and the winning source index
     */
    Model.ResolvedValue resolve(List<Model.SourceRef> candidates);
}
