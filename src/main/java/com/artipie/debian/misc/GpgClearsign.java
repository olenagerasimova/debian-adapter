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
package com.artipie.debian.misc;

import com.jcabi.log.Logger;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.Security;
import java.util.Iterator;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;

/**
 * Gpg signature.
 * @since 0.4
 * @todo #29:30min Main functionality of this class was copy-pasted from
 *  https://github.com/bcgit/bc-java/blob/master/pg/src/main/java/org/bouncycastle/openpgp/examples/ClearSignedFileProcessor.java
 *  Let's refactor this class, step by step, remove not necessary functionality, add javadocs, etc.
 *  The job can be performed in several steps, on the last iteration do not forget to remove this
 *  class from qulice exclusion.
 */
final class GpgClearsign {

    /**
     * Bytes content to sign.
     */
    private final byte[] content;

    /**
     * Ctor.
     * @param content Bytes content to sign
     */
    GpgClearsign(final byte[] content) {
        this.content = content;
    }

    /**
     * Signs content with GPG crearsign signature and returns it along with the signature.
     * @param key Private key bytes
     * @param pass Password
     * @return File, signed with gpg
     */
    byte[] signedContent(final byte[] key, final String pass) {
        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final ArmoredOutputStream armored = new ArmoredOutputStream(out);
            try (
                final InputStream input = new BufferedInputStream(
                    new ByteArrayInputStream(content)
                );
                final ByteArrayOutputStream line = new ByteArrayOutputStream()
            ) {
                final PGPSignatureGenerator sgen = prepareGenerator(key, pass);
                armored.beginClearText(PGPUtil.SHA256);
                int ahead = readInputLine(line, input);
                processLine(armored, sgen, line.toByteArray());
                if (ahead != -1) {
                    do {
                        ahead = readInputLine(line, ahead, input);
                        sgen.update((byte) '\r');
                        sgen.update((byte) '\n');
                        processLine(armored, sgen, line.toByteArray());
                    }
                    while (ahead != -1);
                }
                armored.endClearText();
                BCPGOutputStream bOut = new BCPGOutputStream(armored);
                sgen.generate().encode(bOut);
                armored.close();
                return out.toByteArray();
            }
        } catch (final PGPException err) {
            Logger.error(this, "Error while generating gpg-signature:\n%s", err.getMessage());
            throw new IllegalStateException(err);
        } catch (final IOException err) {
            Logger.error(this, "IO error while generating gpg-signature:\n%s", err.getMessage());
            throw new UncheckedIOException(err);
        }
    }

    /**
     * Signs content with GPG crearsign signature and returns the signature.
     * @param key Private key bytes
     * @param pass Password
     * @return File, signed with gpg
     */
    byte[] signature(final byte[] key, final String pass) {
        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final ArmoredOutputStream armored = new ArmoredOutputStream(out);
            try (
                final InputStream input = new BufferedInputStream(
                    new ByteArrayInputStream(this.content)
                );
                final ByteArrayOutputStream line = new ByteArrayOutputStream()) {
                final PGPSignatureGenerator sgen = prepareGenerator(key, pass);
                int ahead = readInputLine(line, input);
                processLine(sgen, line.toByteArray());
                if (ahead != -1) {
                    do {
                        ahead = readInputLine(line, ahead, input);
                        sgen.update((byte) '\r');
                        sgen.update((byte) '\n');
                        processLine(sgen, line.toByteArray());
                    }
                    while (ahead != -1);
                }
                final BCPGOutputStream res = new BCPGOutputStream(armored);
                sgen.generate().encode(res);
                armored.close();
                return out.toByteArray();
            }
        } catch (final PGPException err) {
            Logger.error(this, "Error while generating gpg-signature:\n%s", err.getMessage());
            throw new IllegalStateException(err);
        } catch (final IOException err) {
            Logger.error(this, "IO error while generating gpg-signature:\n%s", err.getMessage());
            throw new UncheckedIOException(err);
        }
    }

    /**
     * Prepares signature generator.
     * @param key Private key
     * @param pass Password
     * @return Instance of PGPSignatureGenerator
     * @throws IOException On error
     * @throws PGPException On problems with signing
     */
    private static PGPSignatureGenerator prepareGenerator(final byte[] key, final String pass)
        throws IOException, PGPException {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        final PGPSecretKey skey = readSecretKey(new ByteArrayInputStream(key));
        final PGPPrivateKey pkey = skey.extractPrivateKey(
            new JcePBESecretKeyDecryptorBuilder().setProvider("BC").build(pass.toCharArray())
        );
        final PGPSignatureGenerator sgen = new PGPSignatureGenerator(
            new JcaPGPContentSignerBuilder(skey.getPublicKey().getAlgorithm(), PGPUtil.SHA256)
                .setProvider("BC")
        );
        final PGPSignatureSubpacketGenerator ssgen = new PGPSignatureSubpacketGenerator();
        sgen.init(PGPSignature.CANONICAL_TEXT_DOCUMENT, pkey);
        final Iterator<String> it = skey.getPublicKey().getUserIDs();
        if (it.hasNext()) {
            ssgen.setSignerUserID(false, it.next());
            sgen.setHashedSubpackets(ssgen.generate());
        }
        return sgen;
    }

    private static PGPSecretKey readSecretKey(InputStream input) throws IOException, PGPException {
        PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(
            PGPUtil.getDecoderStream(input), new JcaKeyFingerprintCalculator()
        );
        Iterator<PGPSecretKeyRing> keyRingIter = pgpSec.getKeyRings();
        while (keyRingIter.hasNext()) {
            PGPSecretKeyRing keyRing = keyRingIter.next();
            Iterator<PGPSecretKey> keyIter = keyRing.getSecretKeys();
            while (keyIter.hasNext()) {
                PGPSecretKey key = keyIter.next();
                if (key.isSigningKey()) {
                    return key;
                }
            }
        }
        throw new IllegalArgumentException("Can't find signing key in key ring.");
    }

    /**
     * Process line.
     * @param out Where to write
     * @param sign Signature generator
     * @param line Line to process
     * @throws IOException On error
     */
    private static void processLine(final OutputStream out, final PGPSignatureGenerator sign,
        final byte[] line) throws IOException {
        // note: trailing white space needs to be removed from the end of
        // each line for signature calculation RFC 4880 Section 7.1
        int length = getLengthWithoutWhiteSpace(line);
        if (length > 0) {
            sign.update(line, 0, length);
        }
        out.write(line, 0, line.length);
    }

    /**
     * Process line.
     * @param sign Signature generator
     * @param line Line to process
     * @throws IOException On error
     */
    private static void processLine(final PGPSignatureGenerator sign, final byte[] line)
        throws IOException {
        // note: trailing white space needs to be removed from the end of
        // each line for signature calculation RFC 4880 Section 7.1
        int length = getLengthWithoutWhiteSpace(line);
        if (length > 0) {
            sign.update(line, 0, length);
        }
    }

    private static int getLengthWithoutWhiteSpace(final byte[] line) {
        int end = line.length - 1;
        while (end >= 0 && isWhiteSpace(line[end])) {
            end--;
        }
        return end + 1;
    }

    private static boolean isWhiteSpace(byte b) {
        return isLineEnding(b) || b == '\t' || b == ' ';
    }

    private static boolean isLineEnding(byte b) {
        return b == '\r' || b == '\n';
    }

    private static int readInputLine(ByteArrayOutputStream bOut, InputStream fIn)
        throws IOException {
        bOut.reset();
        int lookAhead = -1;
        int ch;
        while ((ch = fIn.read()) >= 0) {
            bOut.write(ch);
            if (ch == '\r' || ch == '\n') {
                lookAhead = readPassedEOL(bOut, ch, fIn);
                break;
            }
        }
        return lookAhead;
    }

    private static int readInputLine(ByteArrayOutputStream bOut, int lookAhead, InputStream fIn)
        throws IOException {
        bOut.reset();
        int ch = lookAhead;
        do {
            bOut.write(ch);
            if (ch == '\r' || ch == '\n') {
                lookAhead = readPassedEOL(bOut, ch, fIn);
                break;
            }
        }
        while ((ch = fIn.read()) >= 0);
        if (ch < 0) {
            lookAhead = -1;
        }
        return lookAhead;
    }

    private static int readPassedEOL(ByteArrayOutputStream bOut, int lastCh, InputStream fIn)
        throws IOException {
        int lookAhead = fIn.read();
        if (lastCh == '\r' && lookAhead == '\n') {
            bOut.write(lookAhead);
            lookAhead = fIn.read();
        }
        return lookAhead;
    }
}
