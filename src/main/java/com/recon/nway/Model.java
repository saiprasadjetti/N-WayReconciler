package main.java.com.recon.nway;

public class Model {
    /** Value from one source (1-based index). */
    public record SourceRef(int source, String value) {}
    /** Chosen value with winning source. */
    public record ResolvedValue(String value, int source) {}
    /** Tiny pair. */
    public record Pair<A,B>(A a, B b) {}
}
