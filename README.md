# N‑Way Source‑Independent Reconciliation (Java, Multithreaded)

This project reconciles **very large CSV files from N sources** without doing an O(total\_rows) cross-join.
It **partitions every file into the same `M` hash buckets by business key**, then reconciles each bucket **in
parallel** and merges the results.

- **Source‑independent**: Any number of input CSVs: `file1.csv file2.csv ... fileN.csv`
- **Scale‑friendly**: Two‑phase *partition → parallel reconcile → merge* pipeline
- **Deterministic**: Same hashing, same chunks → matched keys land together
- **Transparent output**: Annotates the chosen value with its source (e.g., `(S2)`), flags conflicts

> If your data contains quoted commas or complex CSV, swap `CsvUtils` with a production CSV library (e.g., OpenCSV).
> The algorithmic structure remains the same.

---

## Quick Start

```bash
# Build
mvn -q -DskipTests package

# Run (write output + discrepancy report into ./out)
java -jar target/nway-reconciliation-1.0.0.jar   -o out/reconciled.csv   -r out/discrepancies.csv   -k id   -m 16   examples/input_1.csv examples/input_2.csv examples/input_3.csv
```

- `-o` : final reconciled CSV (header + rows)
- `-r` : optional discrepancies report (where sources disagree)
- `-k` : **business key column name** (default: first column)
- `-m` : number of hash buckets/chunks (default: `2 * cores`)
- inputs: two or more CSV files with **the same header** (same columns, same order)

> For truly source‑independent headers, see `ReconEngine#unifyHeader()`—it can be switched to `UNION` instead of `STRICT`.

---

## Data Model & Flow

1. **Partition**: Stream each input CSV, compute `bucket = hash(key) mod M`, append the row to that source’s bucket file
2. **Parallel Reconcile**: For each `bucket in [0..M-1]` in a thread pool:
   - Load bucket rows for all sources into `Map<key, String[]>`
   - For each **union of keys** across sources:
     - For each column:
       - Collect all source values
       - Use `ReconciliationPolicy` to select *resolved value*
       - Annotate provenance, e.g., `(S3)` if chosen from source 3
       - Record a discrepancy if values differ
   - Write a per‑bucket output and discrepancy file
3. **Merge**: Concatenate all bucket outputs (single pass) into final files

All file I/O is streaming; memory usage scales with a single bucket at a time.

---

## Project Layout

```
src/main/java/com/recon/nway/
  App.java                   # CLI & argument parsing
  ReconEngine.java           # Orchestrates partition → parallel reconcile → merge
  ChunkPartitioner.java      # Streams a CSV into M bucket files (per source)
  ChunkReconciler.java       # Reconciles one bucket across N sources
  ReconciliationPolicy.java  # Interface for value-resolution strategy
  DefaultPolicy.java         # First-non-empty, prefer S1; annotate source of chosen value
  CsvUtils.java              # CSV convenience (header, split, join, hashing)
  Model.java                 # Small types (SourceRef, ResolvedValue, Pair)
examples/
  input_1.csv input_2.csv input_3.csv
```

---

## Reconciliation Policy

Default behavior (`DefaultPolicy`):
- For each column among sources: **first non-empty wins**, preferring the **leftmost** source (S1 > S2 > …)
- If chosen from source `Si` where `i > 1`, annotate the output field with ` (Si)`
- If all non-empty values are **different**, this also gets logged to the discrepancy report

You can plug in a different policy by modifying `ReconEngine#policy` (e.g., majority voting, highest quality score, timestamp‑based, etc.).

---

## Limitations & Notes

- Assumes **same header** across sources by default (`STRICT`). Switch to `union` if you need flexible schemas.
- Minimal CSV parser (`CsvUtils.splitCsvLite`) supports basic comma‑separated values without quoted commas or newlines.
  For production, swap with OpenCSV and keep the pipeline intact.
- The algorithm is insensitive to input order. For deterministic output within a bucket, keys are sorted before writing.

---

## Example

`examples/input_1.csv`

```csv
id,name,age,city
1,Alice,30,Delhi
2,Bob,29,Hyderabad
3,Chand,28,Chennai
5,Esha,,Pune
```

`examples/input_2.csv`

```csv
id,name,age,city
1,Alicia,30,Delhi
2,Bob,30,Bengaluru
4,Dev,31,Mumbai
5,Esha,27,
```

`examples/input_3.csv`

```csv
id,name,age,city
2,Bobby,29,Hyderabad
3,Chand,28,Chennai
4,Devendra,31,Mumbai
```

**Run:**

```bash
java -jar target/nway-reconciliation-1.0.0.jar -o out/reconciled.csv -r out/discrepancies.csv -k id -m 4 examples/input_*.csv
```

**Reconciled output (illustrative):**

```csv
id,name,age,city
1,Alicia (S2),30,Delhi
2,Bob,29,Hyderabad
3,Chand,28,Chennai
4,Dev (S2),31,Mumbai
5,Esha,27 (S2),Pune
```

**Discrepancies (illustrative):**

```csv
key,column,values
1,name,[S1=Alice|S2=Alicia]
2,name,[S2=Bob|S3=Bobby]
4,name,[S2=Dev|S3=Devendra]
5,age,[S1=|S2=27]
5,city,[S1=Pune|S2=]
```

---

## Class-by-Class Explanation

- **`App`**: Parses CLI flags, validates inputs, and kicks off the engine.
- **`ReconEngine`**: High‑level orchestration. Creates temp dirs, partitions each source file into M buckets, spins a fixed thread pool to reconcile each bucket, then merges per‑bucket outputs into final CSV(s).
- **`ChunkPartitioner`**: Single-pass streaming of an input CSV into M bucket files by hashing the business key.
- **`ChunkReconciler`**: Given bucket `b`, reads all `source_i.bucket_b.csv`, builds maps, reconciles rows column-by-column via `ReconciliationPolicy`, writes per-bucket outputs, and records discrepancies.
- **`ReconciliationPolicy`**: Strategy interface for picking a single value from N candidates with provenance.
- **`DefaultPolicy`**: First‑non‑empty wins (prefer S1), annotate with `(Si)` if the chosen value did not come from S1.
- **`CsvUtils`**: Helpers for reading headers, splitting/joining CSV safely enough for this demo, computing consistent bucket IDs, and writing lines.
- **`Model`**: Small immutable helper records: `SourceRef`, `ResolvedValue`, `Pair`.

---

## License

MIT
