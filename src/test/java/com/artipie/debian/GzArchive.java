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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

/**
 * Class to work with gz: pack and unpack bytes.
 * @since 0.6
 * @checkstyle NonStaticMethodCheck (500 lines)
 */
public final class GzArchive {

    /**
     * Compresses provided bytes in gz format.
     * @param data Bytes to pack
     * @return Packed bytes
     */
    public byte[] compress(final byte[] data) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GzipCompressorOutputStream gcos =
            new GzipCompressorOutputStream(new BufferedOutputStream(baos))) {
            gcos.write(data);
        } catch (final IOException err) {
            throw new UncheckedIOException(err);
        }
        return baos.toByteArray();
    }

    /**
     * Decompresses provided gz packed data.
     * @param data Bytes to unpack
     * @return Unpacked data in string format
     * @checkstyle MagicNumberCheck (15 lines)
     */
    @SuppressWarnings("PMD.AssignmentInOperand")
    public String decompress(final byte[] data) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (
            GzipCompressorInputStream gcis = new GzipCompressorInputStream(
                new BufferedInputStream(new ByteArrayInputStream(data))
            )
        ) {
            final byte[] buf = new byte[1024];
            int cnt;
            while (-1 != (cnt = gcis.read(buf))) {
                out.write(buf, 0, cnt);
            }
        } catch (final IOException err) {
            throw new UncheckedIOException(err);
        }
        return out.toString();
    }
}
