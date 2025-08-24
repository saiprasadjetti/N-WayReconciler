package main.java.com.recon.nway;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * CLI entrypoint.
 *
 * Usage:
 *   java -jar nway-reconciliation.jar -o out.csv -r discrepancies.csv -k id -m 16 file1.csv file2.csv ...
 */
public class App {
    public static void main(String[] args) throws Exception {
        Arguments a = Arguments.parse(args);
        if (a.inputs.size() < 2) {
            System.err.println("Provide at least two CSV input files.");
            System.exit(2);
        }
        Files.createDirectories(a.outputPath().getParent());
        if (a.reportPath() != null) {
            Files.createDirectories(a.reportPath().getParent());
        }
        ReconEngine engine = new ReconEngine(a);
        engine.run();
        System.out.println("Done.\nOutput : " + a.output + (a.reportPath() != null ? ("\nReport : " + a.report) : ""));
    }

    /** Simple arg parser (no external deps). */
    static class Arguments {
        String output = "reconciled.csv";
        String report = null;
        String keyColumn = null; // null -> first column
        int buckets = Math.max(2, Runtime.getRuntime().availableProcessors() * 2);
        List<Path> inputs = new ArrayList<>();

        static Arguments parse(String[] args) {
            Arguments a = new Arguments();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-o":
                    case "--output":
                        a.output = args[++i];
                        break;
                    case "-r":
                    case "--report":
                        a.report = args[++i];
                        break;
                    case "-k":
                    case "--key":
                        a.keyColumn = args[++i];
                        break;
                    case "-m":
                    case "--buckets":
                        a.buckets = Integer.parseInt(args[++i]);
                        if (a.buckets <= 0) throw new IllegalArgumentException("buckets must be > 0");
                        break;
                    default:
                        a.inputs.add(Paths.get(args[i]));
                }
            }
            if (a.inputs.isEmpty()) {
                throw new IllegalArgumentException("No input files provided.");
            }
            return a;
        }

        Path outputPath() { return Paths.get(output); }
        Path reportPath() { return report == null ? null : Paths.get(report); }
    }
}
