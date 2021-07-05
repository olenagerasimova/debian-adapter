/*
 * The MIT License (MIT) Copyright (c) 2020-2021 artipie.com
 * https://github.com/artipie/debian-adapter/LICENSE.txt
 */
package com.artipie.debian.benchmarks;

import com.amihaiemil.eoyaml.Yaml;
import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.debian.Config;
import com.artipie.debian.Debian;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.cactoos.scalar.Unchecked;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmark for {@link com.artipie.debian.Debian.Asto}.
 * @since 0.8
 * @checkstyle DesignForExtensionCheck (500 lines)
 * @checkstyle JavadocMethodCheck (500 lines)
 * @checkstyle MagicNumberCheck (500 lines)
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5)
@Measurement(iterations = 20)
public class RepoUpdateBench {

    /**
     * Benchmark directory.
     */
    private static final String BENCH_DIR = System.getenv("BENCH_DIR");

    /**
     * Storage.
     */
    private Storage storage;

    /**
     * Deb packages list.
     */
    private List<Key> debs;

    /**
     * Count from unique names of Packages index.
     */
    private AtomicInteger count;

    @Setup
    public void setup() throws IOException {
        if (RepoUpdateBench.BENCH_DIR == null) {
            throw new IllegalStateException("BENCH_DIR environment variable must be set");
        }
        this.storage = new InMemoryStorage();
        this.count = new AtomicInteger(0);
        try (Stream<Path> files = Files.list(Paths.get(RepoUpdateBench.BENCH_DIR))) {
            this.debs = new ArrayList<>(150);
            files.forEach(
                item -> {
                    final Key key = new Key.From(item.getFileName().toString());
                    this.storage.save(
                        key,
                        new Content.From(
                            new Unchecked<>(() -> Files.readAllBytes(item)).value()
                        )
                    ).join();
                    if (key.string().endsWith(".deb")) {
                        this.debs.add(key);
                    }
                }
            );
        }
    }

    @Benchmark
    public void run(final Blackhole bhl) {
        final Debian deb = new Debian.Asto(
            this.storage,
            new Config.FromYaml(
                "my-deb",
                Yaml.createYamlMappingBuilder().add("Architectures", "amd64")
                    .add("Components", "main").build(),
                new InMemoryStorage()
            )
        );
        deb.updatePackages(
            this.debs,
            new Key.From(String.format("Packages-%s.gz", this.count.incrementAndGet()))
        ).toCompletableFuture().join();
        deb.generateRelease().toCompletableFuture().join();
    }

}
