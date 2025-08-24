package main.java.com.recon.nway;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Streams one CSV into M bucket files based on hash(key). */
public class ChunkPartitioner {

    /**
     * @param source     input CSV
     * @param tempRoot   temp directory
     * @param sourceIdx  1-based source index (1..N) for naming
     * @param buckets    number of hash buckets
     * @param keyIdx     index of key column in the header
     * @return list of paths to bucket files for this source, size = buckets
     */
    public static List<Path> partition(Path source, Path tempRoot, int sourceIdx, int buckets, int keyIdx) throws IOException {
        Path srcDir = tempRoot.resolve("source_" + sourceIdx);
        Files.createDirectories(srcDir);
        // Prepare writers for each bucket
        List<Path> bucketFiles = new ArrayList<>(buckets);
        List<BufferedWriter> writers = new ArrayList<>(buckets);
        for (int b = 0; b < buckets; b++) {
            Path p = srcDir.resolve("bucket_" + b + ".csv");
            bucketFiles.add(p);
            writers.add(Files.newBufferedWriter(p, StandardCharsets.UTF_8));
        }

        try (BufferedReader br = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            String header = br.readLine(); // skip header (already validated by engine)
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = CsvUtils.splitCsvLite(line);
                if (keyIdx >= parts.length) continue; // malformed
                String key = parts[keyIdx];
                int bucket = CsvUtils.bucketForKey(key, buckets);
                writers.get(bucket).write(line);
                writers.get(bucket).newLine();
            }
        } finally {
            for (BufferedWriter w : writers) { try { w.close(); } catch (IOException ignored) {} }
        }
        return bucketFiles;
    }
}
