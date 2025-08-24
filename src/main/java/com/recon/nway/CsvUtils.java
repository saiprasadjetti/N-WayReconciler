package main.java.com.recon.nway;

import java.util.*;
import java.nio.charset.StandardCharsets;

/** Lightweight CSV helpers. Replace with a full CSV library if needed. */
public class CsvUtils {
    /** Very simple split: no quoted comma/newline support (demo only). */
    public static String[] splitCsvLite(String line) {
        // Trim trailing CR if present
        if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
        return line.split(",", -1); // keep empty
    }

    public static String joinCsv(String[] parts) {
        return String.join(",", escapeAll(Arrays.asList(parts)));
    }

    public static List<String> escapeAll(List<String> values) {
        List<String> out = new ArrayList<>(values.size());
        for (String v : values) out.add(escape(v));
        return out;
    }

    public static String escape(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            v = v.replace("\"", "\"\"");
            return "\"" + v + "\"";
        }
        return v;
    }

    public static int indexOf(String[] header, String column) {
        for (int i = 0; i < header.length; i++) if (header[i].equals(column)) return i;
        return -1;
    }

    public static int bucketForKey(String key, int buckets) {
        // Stable hashing across JVMs
        int h = key == null ? 0 : key.hashCode();
        // Spread bits
        h ^= (h >>> 16);
        int m = Math.abs(h) % buckets;
        return m;
    }
}
