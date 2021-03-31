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
package com.artipie.debian;

import com.artipie.debian.metadata.ControlField;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * MultiDebian merges metadata.
 * @since 0.6
 */
public interface MultiDebian {

    /**
     * Merges provided indexes.
     * @param items Items to merge
     * @param res Output stream with merged data
     * @throws IOException On IO error
     */
    void merge(Collection<InputStream> items, OutputStream res) throws IOException;

    /**
     * Implementation of {@link MultiDebian} that merges Packages indexes. Implementation
     * does not close input or output streams, these operations should be made from the outside.
     * @since 0.6
     */
    final class MergedPackages implements MultiDebian {

        @Override
        public void merge(final Collection<InputStream> items, final OutputStream res)
            throws IOException {
            final GZIPOutputStream gop = new GZIPOutputStream(res);
            final Map<String, String> packages = new HashMap<>(items.size());
            for (final InputStream inp : items) {
                MergedPackages.appendPackages(gop, inp, packages);
            }
            gop.finish();
        }

        /**
         * Appends items from provided InputStream to OutputStream, duplicated packages are not
         * appended.
         * @param out OutputStream to write the result
         * @param inp InputStream to read Packages index from
         * @param packages Map with the appended packages
         * @throws IOException On IO error
         */
        private static void appendPackages(
            final OutputStream out, final InputStream inp, final Map<String, String> packages
        ) throws IOException {
            final GZIPInputStream gis = new GZIPInputStream(inp);
            final BufferedReader rdr =
                new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8));
            String line;
            StringBuilder item = new StringBuilder();
            do {
                line = rdr.readLine();
                if (line == null || line.isEmpty()) {
                    final String name = new ControlField.Package().value(item.toString()).get(0);
                    final String version = new ControlField.Version().value(item.toString()).get(0);
                    if (!(packages.containsKey(name) && packages.get(name).equals(version))) {
                        out.write(item.append('\n').toString().getBytes(StandardCharsets.UTF_8));
                        packages.put(name, version);
                    }
                    item = new StringBuilder();
                } else {
                    item.append(line).append('\n');
                }
            } while (line != null);
        }
    }

}
