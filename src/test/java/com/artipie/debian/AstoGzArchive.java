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

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.blocking.BlockingStorage;
import java.nio.charset.StandardCharsets;

/**
 * GzArchive: packs or unpacks.
 * @since 0.4
 */
public final class AstoGzArchive {

    /**
     * Abstract storage.
     */
    private final Storage asto;

    /**
     * Ctor.
     * @param asto Abstract storage
     */
    public AstoGzArchive(final Storage asto) {
        this.asto = asto;
    }

    /**
     * Compress provided bytes in gz format and adds item to storage by provided key.
     * @param bytes Bytes to pack
     * @param key Storage key
     */
    public void packAndSave(final byte[] bytes, final Key key) {
        this.asto.save(key, new Content.From(new GzArchive().compress(bytes))).join();
    }

    /**
     * Compress provided string in gz format and adds item to storage by provided key.
     * @param content String to pack
     * @param key Storage key
     */
    public void packAndSave(final String content, final Key key) {
        this.packAndSave(content.getBytes(StandardCharsets.UTF_8), key);
    }

    /**
     * Unpacks storage item and returns unpacked content as string.
     * @param key Storage item
     * @return Unpacked string
     */
    public String unpack(final Key key) {
        return new GzArchive().decompress(new BlockingStorage(this.asto).value(key));
    }
}
