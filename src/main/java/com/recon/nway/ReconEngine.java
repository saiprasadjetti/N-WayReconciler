package main.java.com.recon.nway;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/** Orchestrates partition → parallel reconcile → merge. */
public class ReconEngine {
    private final App.Arguments args;
    private final ReconciliationPolicy policy = new DefaultPolicy();

    public ReconEngine(App.Arguments args) {
        this.args = args;
    }

    public void run() throws Exception {
        final Path tempRoot = Files.createTempDirectory("nway_recon_");
        final int sources = args.inputs.size();
        System.out.println("Temp dir: " + tempRoot.toAbsolutePath());

        // 1) Read and validate headers; determine key column index
        List<String[]> headers = new ArrayList<>();
        for (Path p : args.inputs) {
            try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                String header = br.readLine();
                if (header == null) throw new IllegalArgumentException("Empty file: " + p);
                headers.add(CsvUtils.splitCsvLite(header));
            }
        }
        String[] refHeader = headers.get(0);
        int keyIdx;
        if (args.keyColumn != null) {
            keyIdx = CsvUtils.indexOf(refHeader, args.keyColumn);
            if (keyIdx < 0) throw new IllegalArgumentException("Key column '" + args.keyColumn + "' not found in header.");
        } else {
            keyIdx = 0;
        }
        // Enforce STRICT same header (can be relaxed to union if needed)
        for (int i = 1; i < headers.size(); i++) {
            if (!Arrays.equals(refHeader, headers.get(i))) {
                throw new IllegalArgumentException("All inputs must share the same header (STRICT). "
                        + "Mismatch between file 1 and file " + (i + 1));
            }
        }

        // 2) Partition all sources into M bucket files
        List<List<Path>> sourceBuckets = new ArrayList<>();
        for (int s = 0; s < sources; s++) {
            List<Path> buckets = ChunkPartitioner.partition(
                    args.inputs.get(s), tempRoot, s + 1, args.buckets, keyIdx);
            sourceBuckets.add(buckets);
        }

        // 3) Reconcile each bucket in parallel
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(args.buckets, Runtime.getRuntime().availableProcessors()));
        List<Future<ChunkReconciler.Result>> futures = new ArrayList<>();
        for (int b = 0; b < args.buckets; b++) {
            final int bucketId = b;
            futures.add(pool.submit(() -> {
                List<Path> perSource = new ArrayList<>(sources);
                for (int s = 0; s < sources; s++) perSource.add(sourceBuckets.get(s).get(bucketId));
                ChunkReconciler cr = new ChunkReconciler(bucketId, perSource, refHeader, keyIdx, policy, tempRoot);
                return cr.call();
            }));
        }
        pool.shutdown();
        List<ChunkReconciler.Result> results = new ArrayList<>();
        for (Future<ChunkReconciler.Result> f : futures) results.add(f.get());

        // 4) Merge bucket outputs
        Files.createDirectories(args.outputPath().getParent() == null ? Paths.get(".") : args.outputPath().getParent());
        try (BufferedWriter out = Files.newBufferedWriter(args.outputPath(), StandardCharsets.UTF_8)) {
            out.write(String.join(",", refHeader));
            out.newLine();
            // deterministic bucket order
            results.sort(Comparator.comparingInt(r -> r.bucketId()));
            for (ChunkReconciler.Result r : results) {
                try (BufferedReader br = Files.newBufferedReader(r.outputCsv(), StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = br.readLine()) != null) out.write(line + System.lineSeparator());
                }
            }
        }

        if (args.reportPath() != null) {
            Files.createDirectories(args.reportPath().getParent() == null ? Paths.get(".") : args.reportPath().getParent());
            try (BufferedWriter rep = Files.newBufferedWriter(args.reportPath(), StandardCharsets.UTF_8)) {
                rep.write("key,column,values");
                rep.newLine();
                results.sort(Comparator.comparingInt(r -> r.bucketId()));
                for (ChunkReconciler.Result r : results) {
                    try (BufferedReader br = Files.newBufferedReader(r.discrepancyCsv(), StandardCharsets.UTF_8)) {
                        String line;
                        while ((line = br.readLine()) != null) rep.write(line + System.lineSeparator());
                    }
                }
            }
        }

        // 5) Clean up temp files
        // (Comment the next line if you want to inspect bucket files)
        deleteRecursively(tempRoot);
    }

    private static void deleteRecursively(Path p) throws IOException {
        if (!Files.exists(p)) return;
        Files.walk(p)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                });
    }
}
