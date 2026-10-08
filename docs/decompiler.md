# Decompiler adapter

`VineflowerDecompiler` implements the project `Decompiler` interface. It first
uses `JarIndex` to verify the requested class and its release selection, then
passes the JAR to Vineflower. A memory-backed result saver retains only the
requested source.

Archive limits reject JARs over the configured compressed size or entry count,
class entries over the per-class byte limit, and staged class data over the
aggregate expanded-byte limit. A shared semaphore caps Vineflower runs across
foreground requests and background warm-up. Each run receives an explicit
thread count. Because Vineflower runs in-process, JARoscope does not promise a
hard per-run timeout; Java interruption cannot safely stop arbitrary library
work. Archive and concurrency limits bound the input and resource footprint.

```mermaid
sequenceDiagram
    participant Caller
    participant Adapter as VineflowerDecompiler
    participant Index as JarIndex
    participant VF as Vineflower
    participant Saver as SourceSaver
    Caller->>Adapter: decompile(jar, class, release)
    Adapter->>Index: select class entry
    Index-->>Adapter: selected entry
    Adapter->>VF: decompile JAR
    VF->>Saver: source callbacks
    Saver-->>Adapter: requested class source
    Adapter-->>Caller: DecompiledClass
```

The adapter does not write output files or load application classes. The
single-class path uses one Vineflower invocation. The batch path stages two
temporary release-resolved JAR views: requested classes as input, and all
selected classes as library context. Vineflower is invoked once for the batch,
and its result saver forwards each requested class as it is emitted. The
temporary views are deleted when the invocation ends.
