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
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.debian;

import com.artipie.asto.Storage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.debian.http.DebianSlice;
import com.artipie.http.slice.LoggingSlice;
import com.artipie.vertx.VertxSliceServer;
import io.vertx.reactivex.core.Vertx;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.cactoos.list.ListOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.text.StringContainsInOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;

/**
 * Test for {@link com.artipie.debian.http.DebianSlice}.
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@Disabled
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public final class DebianSliceITCase {

    /**
     * Vertx instance.
     */
    private static final Vertx VERTX = Vertx.vertx();

    /**
     * Temporary directory for all tests.
     * @checkstyle VisibilityModifierCheck (3 lines)
     */
    @TempDir
    Path tmp;

    /**
     * Vertx slice server instance.
     */
    private VertxSliceServer server;

    /**
     * Container.
     */
    private GenericContainer<?> cntn;

    @BeforeEach
    void init() throws IOException, InterruptedException {
        final Storage storage = new InMemoryStorage();
        this.server = new VertxSliceServer(
            DebianSliceITCase.VERTX,
            new LoggingSlice(new DebianSlice(storage))
        );
        new TestResource("pspp_1.2.0-3_i386.deb").saveTo(storage);
        final int port = this.server.start();
        Testcontainers.exposeHostPorts(port);
        final Path setting = this.tmp.resolve("sources.list");
        Files.write(
            setting,
            String.format(
                "deb [trusted=yes] http://host.testcontainers.internal:%d/ artipie main", port
            ).getBytes()
        );
        this.cntn = new GenericContainer<>("debian")
            .withCommand("tail", "-f", "/dev/null")
            .withWorkingDirectory("/home/")
            .withFileSystemBind(this.tmp.toString(), "/home");
        this.cntn.start();
        this.cntn.execInContainer("mv", "/home/sources.list", "/etc/apt/");
        this.cntn.execInContainer("apt-get", "update");
    }

    @Test
    void installs() throws IOException, InterruptedException {
        MatcherAssert.assertThat(
            this.cntn.execInContainer(
                "apt-get", "--verbose-versions", "install", "pspp"
            ).toString(),
            new StringContainsInOrder(new ListOf<>("installed", "successfully"))
        );
    }

    @AfterEach
    void stop() {
        this.server.stop();
        this.cntn.stop();
    }
}
