/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Artipie
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.debian.metadata;

import com.artipie.asto.Copy;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.fs.FileStorage;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.io.FileUtils;
import org.cactoos.list.ListOf;

/**
 * Implementation of {@link Package} that checks uniqueness of the packages index records.
 * @since 0.5
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @checkstyle ExecutableStatementCountCheck (500 lines)
 */
public final class UniquePackage implements Package {

    /**
     * Abstract storage.
     */
    private final Storage asto;

    /**
     * Ctor.
     * @param asto Abstract storage
     */
    public UniquePackage(final Storage asto) {
        this.asto = asto;
    }

    @Override
    public CompletionStage<Void> add(final Iterable<String> items, final Key index) {
        return this.asto.exists(index).thenCompose(
            exists -> {
                final CompletionStage<Void> res;
                if (exists) {
                    try {
                        final Path temp = Files.createTempDirectory("packages-");
                        final Path latest = Files.createTempFile(temp, "latest-", ".gz");
                        res = new Copy(this.asto, new ListOf<>(index))
                            .copy(new FileStorage(temp))
                            .thenAccept(
                                nothing -> decompressAppendCompress(
                                    temp.resolve(index.string()), latest, items
                                )
                            ).thenCompose(
                                nothing -> new FileStorage(temp)
                                    .move(new Key.From(latest.getFileName().toString()), index)
                            ).thenCompose(
                                nothing -> new Copy(new FileStorage(temp), new ListOf<>(index))
                                    .copy(this.asto)
                            ).thenAccept(nothing -> FileUtils.deleteQuietly(temp.toFile()));
                    } catch (final IOException err) {
                        throw new IllegalStateException("Failed to create temp dir", err);
                    }
                } else {
                    res = new Package.Asto(this.asto).add(items, index);
                }
                return res;
            }
        );
    }

    /**
     * Decompresses Packages.gz file, checks the duplicates, appends information and writes
     * compressed result into new file.
     * @param decompress File to decompress
     * @param res Where to write the result
     * @param items Items to append
     * @return List of the `Filename`s fields of the duplicated packages.
     */
    @SuppressWarnings({"PMD.AssignmentInOperand", "PMD.CyclomaticComplexity"})
    private static List<String> decompressAppendCompress(
        final Path decompress, final Path res, final Iterable<String> items
    ) {
        final byte[] bytes = String.join("\n\n", items).getBytes(StandardCharsets.UTF_8);
        final Map<String, String> newbies = StreamSupport.stream(items.spliterator(), false)
            .collect(
                Collectors.toMap(
                    item -> new ControlField.Package().value(item).get(0),
                    item -> new ControlField.Version().value(item).get(0)
                )
            );
        final List<String> duplicates = new ArrayList<>(5);
        try (
            GZIPInputStream gis = new GZIPInputStream(Files.newInputStream(decompress));
            BufferedReader rdr =
                new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8));
            GZIPOutputStream gop =
                new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(res)))
        ) {
            String line;
            StringBuilder item = new StringBuilder();
            while ((line = rdr.readLine()) != null) {
                if (line.isEmpty()) {
                    final Optional<String> dupl = UniquePackage.duplicate(item.toString(), newbies);
                    if (dupl.isPresent()) {
                        duplicates.add(dupl.get());
                    } else {
                        gop.write(item.append('\n').toString().getBytes(StandardCharsets.UTF_8));
                    }
                    item = new StringBuilder();
                } else {
                    item.append(line).append('\n');
                }
            }
            if (item.length() > 0) {
                final Optional<String> dupl = UniquePackage.duplicate(item.toString(), newbies);
                if (dupl.isPresent()) {
                    duplicates.add(dupl.get());
                } else {
                    gop.write(item.append('\n').toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            gop.write(bytes);
        } catch (final UnsupportedEncodingException err) {
            throw new IllegalStateException(err);
        } catch (final IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        return duplicates;
    }

    /**
     * Checks whether item is present in the list of new packages to add. If so, returns package
     * `Filename` field.
     * @param item Packages item to check
     * @param newbies Newly added packages names and versions
     * @return Filename field value if package is a duplicate
     */
    private static Optional<String> duplicate(
        final String item, final Map<String, String> newbies
    ) {
        final String npackage = new ControlField.Package().value(item).get(0);
        Optional<String> res = Optional.empty();
        if (newbies.containsKey(npackage)
            && newbies.get(npackage).equals(new ControlField.Version().value(item).get(0))) {
            res = Optional.of(new ControlField.Filename().value(item).get(0));
        }
        return res;
    }
}
