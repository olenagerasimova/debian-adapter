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

import com.amihaiemil.eoyaml.Yaml;
import com.artipie.asto.Storage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.debian.http.DebianSlice;
import com.artipie.http.auth.Authentication;
import com.artipie.http.auth.Permissions;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.slice.LoggingSlice;
import com.artipie.vertx.VertxSliceServer;
import io.vertx.reactivex.core.Vertx;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.codec.binary.Base64;
import org.cactoos.list.ListOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.hamcrest.text.StringContainsInOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;

/**
 * Test for {@link DebianSlice}.
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
@EnabledOnOs({OS.LINUX, OS.MAC})
public final class DebianAuthSliceITCase {

    /**
     * Vertx instance.
     */
    private static final Vertx VERTX = Vertx.vertx();

    /**
     * User name.
     */
    private static final String USER = "alice";

    /**
     * Password.
     */
    private static final String PSWD = "123";

    /**
     * Auth.
     */
    private static final String AUTH = String.format(
        "%s:%s", DebianAuthSliceITCase.USER, DebianAuthSliceITCase.PSWD
    );

    /**
     * Temporary directory for all tests.
     * @checkstyle VisibilityModifierCheck (3 lines)
     */
    @TempDir
    Path tmp;

    /**
     * Artipie port.
     */
    private int port;

    /**
     * Vertx slice server instance.
     */
    private VertxSliceServer server;

    /**
     * Container.
     */
    private GenericContainer<?> cntn;

    @Test
    void pushAndInstallWorks() throws Exception {
        this.init((user, action) -> true);
        final HttpURLConnection con = this.getConnection(DebianAuthSliceITCase.AUTH, "PUT");
        final DataOutputStream out = new DataOutputStream(con.getOutputStream());
        out.write(new TestResource("aglfn_1.7-3_amd64.deb").asBytes());
        out.close();
        MatcherAssert.assertThat(
            "Response for upload is OK",
            con.getResponseCode(),
            new IsEqual<>(Integer.parseInt(RsStatus.OK.code()))
        );
        this.cntn.execInContainer("apt-get", "update");
        final Container.ExecResult res =
            this.cntn.execInContainer("apt-get", "install", "-y", "aglfn");
        MatcherAssert.assertThat(
            "Package was downloaded and unpacked",
            res.getStdout(),
            new StringContainsInOrder(new ListOf<>("Unpacking aglfn", "Setting up aglfn"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT"})
    void returnsUnauthorizedWhenUserIsUnknown(final String method) throws Exception {
        this.init((user, action) -> user.name().equals(DebianAuthSliceITCase.USER));
        MatcherAssert.assertThat(
            "Response is UNAUTHORIZED",
            this.getConnection("mark:abc", method).getResponseCode(),
            new IsEqual<>(Integer.parseInt(RsStatus.UNAUTHORIZED.code()))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT"})
    void returnsForbiddenWhenOperationIsNotAllowed(final String method) throws Exception {
        this.init(
            (user, action) -> user.name().equals(DebianAuthSliceITCase.USER)
                && action.equals("fake")
        );
        MatcherAssert.assertThat(
            "Response is FORBIDDEN",
            this.getConnection(DebianAuthSliceITCase.AUTH, method).getResponseCode(),
            new IsEqual<>(Integer.parseInt(RsStatus.FORBIDDEN.code()))
        );
    }

    @AfterEach
    void stop() {
        this.server.stop();
        this.cntn.stop();
    }

    private HttpURLConnection getConnection(final String auth, final String method)
        throws IOException {
        final HttpURLConnection con = (HttpURLConnection) new URL(
            String.format("http://localhost:%d/main/aglfn_1.7-3_amd64.deb", this.port)
        ).openConnection();
        con.setDoOutput(true);
        con.addRequestProperty(
            "Authorization",
            String.format(
                "Basic %s",
                new String(
                    Base64.encodeBase64(auth.getBytes(StandardCharsets.UTF_8))
                )
            )
        );
        con.setRequestMethod(method);
        return con;
    }

    private void init(final Permissions permissions) throws IOException, InterruptedException {
        final Storage storage = new InMemoryStorage();
        this.server = new VertxSliceServer(
            DebianAuthSliceITCase.VERTX,
            new LoggingSlice(
                new DebianSlice(
                    storage,
                    permissions,
                    new Authentication.Single(
                        DebianAuthSliceITCase.USER, DebianAuthSliceITCase.PSWD
                    ),
                    new Config.FromYaml(
                        "artipie",
                        Yaml.createYamlMappingBuilder()
                            .add("Components", "main")
                            .add("Architectures", "amd64")
                            .build()
                    )
                )
            )
        );
        this.port = this.server.start();
        Testcontainers.exposeHostPorts(this.port);
        final Path setting = this.tmp.resolve("sources.list");
        Files.write(
            setting,
            String.format(
                "deb [trusted=yes] http://%s@host.testcontainers.internal:%d/ artipie main",
                DebianAuthSliceITCase.AUTH, this.port
            ).getBytes()
        );
        this.cntn = new GenericContainer<>("debian")
            .withCommand("tail", "-f", "/dev/null")
            .withWorkingDirectory("/home/")
            .withFileSystemBind(this.tmp.toString(), "/home");
        this.cntn.start();
        this.cntn.execInContainer("mv", "/home/sources.list", "/etc/apt/");
        this.cntn.execInContainer("ls", "-la", "/etc/apt/");
        this.cntn.execInContainer("cat", "/etc/apt/sources.list");
    }
}
