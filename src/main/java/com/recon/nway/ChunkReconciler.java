package main.java.com.recon.nway;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/** Reconciles one bucket across N sources. */
public class ChunkReconciler implements java.util.concurrent.Callable<ChunkReconciler.Result> {
    private final int bucketId;
    private final List<Path> sourceBucketFiles; // per source
    private final String[] header;
    private final int keyIdx;
    private final ReconciliationPolicy policy;
    private final Path tempRoot;

    public record Result(int bucketId, Path outputCsv, Path discrepancyCsv) {}

    public ChunkReconciler(int bucketId,
                           List<Path> sourceBucketFiles,
                           String[] header,
                           int keyIdx,
                           ReconciliationPolicy policy,
                           Path tempRoot) {
        this.bucketId = bucketId;
        this.sourceBucketFiles = sourceBucketFiles;
        this.header = header;
        this.keyIdx = keyIdx;
        this.policy = policy;
        this.tempRoot = tempRoot;
    }

    @Override
    public Result call() throws Exception {
        // Load maps for each source
        List<Map<String, String[]>> perSource = new ArrayList<>();
        for (Path p : sourceBucketFiles) {
            perSource.add(loadMap(p));
        }
        // Union of keys
        Set<String> allKeys = new TreeSet<>();
        for (Map<String, String[]> m : perSource) allKeys.addAll(m.keySet());

        Path outDir = tempRoot.resolve("bucket_out");
        Files.createDirectories(outDir);
        Path outCsv = outDir.resolve("bucket_" + bucketId + "_out.csv");
        Path discCsv = outDir.resolve("bucket_" + bucketId + "_disc.csv");

        try (BufferedWriter out = Files.newBufferedWriter(outCsv, StandardCharsets.UTF_8);
             BufferedWriter disc = Files.newBufferedWriter(discCsv, StandardCharsets.UTF_8)) {

            for (String key : allKeys) {
                // gather candidates column by column
                List<String> resolvedRow = new ArrayList<>(header.length);
                // Ensure key appears exactly once at keyIdx
                for (int c = 0; c < header.length; c++) {
                    if (c == keyIdx) {
                        resolvedRow.add(key);
                        continue;
                    }
                    // collect all source values for column c
                    List<Model.SourceRef> candidates = new ArrayList<>();
                    for (int s = 0; s < perSource.size(); s++) {
                        String[] row = perSource.get(s).get(key);
                        String v = (row != null && c < row.length) ? row[c] : "";
                        candidates.add(new Model.SourceRef(s + 1, v == null ? "" : v));
                    }
                    Model.ResolvedValue chosen = policy.resolve(candidates);
                    resolvedRow.add(chosen.value());
                    // discrepancy?
                    if (hasDisagreement(candidates)) {
                        disc.write(key + "," + header[c] + ",[" + formatValues(candidates) + "]");
                        disc.newLine();
                    }
                }
                out.write(String.join(",", CsvUtils.escapeAll(resolvedRow)));
                out.newLine();
            }
        }
        return new Result(bucketId, outCsv, discCsv);
    }

    private static boolean hasDisagreement(List<Model.SourceRef> candidates) {
        String ref = null;
        for (Model.SourceRef sr : candidates) {
            String v = (sr.value() == null ? "" : sr.value());
            if (ref == null && !v.isEmpty()) ref = v;
            else if (!v.isEmpty() && !v.equals(ref)) return true;
        }
        return false;
    }

    private static String formatValues(List<Model.SourceRef> candidates) {
        List<String> parts = new ArrayList<>();
        for (Model.SourceRef sr : candidates) {
            parts.add("S" + sr.source() + "=" + (sr.value() == null ? "" : sr.value()));
        }
        return String.join("|", parts);
    }

    private Map<String, String[]> loadMap(Path p) throws IOException {
        Map<String, String[]> map = new HashMap<>();
        if (!Files.exists(p)) return map;
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = CsvUtils.splitCsvLite(line);
                if (keyIdx >= parts.length) continue;
                map.put(parts[keyIdx], parts);
            }
        }
        return map;
    }
}
