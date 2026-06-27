/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth;

import com.sun.net.httpserver.HttpServer;
import dev.chojo.ember.conf.file.elements.HibpSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HibpClientTest {

    private HttpServer server;
    private final AtomicReference<String> bodyToServe = new AtomicReference<>("");
    private final AtomicReference<Integer> statusToServe = new AtomicReference<>(200);
    private final AtomicReference<String> lastRequestedPath = new AtomicReference<>("");

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/range/", exchange -> {
            lastRequestedPath.set(exchange.getRequestURI().getPath());
            byte[] response = bodyToServe.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusToServe.get(), response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private HibpClient newClient(boolean enabled) {
        var config = new HibpSettings() {
            @Override
            public boolean enabled() {
                return enabled;
            }

            @Override
            public int staleAfterDays() {
                return 30;
            }

            @Override
            public String endpoint() {
                return "http://" + server.getAddress().getHostString() + ":"
                        + server.getAddress().getPort() + "/range/";
            }

            @Override
            public int timeoutSeconds() {
                return 2;
            }
        };
        return new HibpClient(config);
    }

    private static String suffixOf(String plaintext) throws Exception {
        var digest = MessageDigest.getInstance("SHA-1");
        byte[] bytes = digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT).substring(5);
    }

    @Test
    void pwnedSuffixMatched() throws Exception {
        String plaintext = "P@ssw0rd-test-123";
        String suffix = suffixOf(plaintext);
        bodyToServe.set(suffix + ":42\r\nABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFA:1\r\n");

        assertTrue(newClient(true).isPwned(plaintext));
    }

    @Test
    void noMatchReturnsFalse() {
        bodyToServe.set("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA:1\r\nBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB:7\r\n");
        assertFalse(newClient(true).isPwned("Some-fresh-passphrase-abc-456"));
    }

    @Test
    void disabledShortCircuits() {
        bodyToServe.set("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA:1\r\n");
        var client = newClient(false);
        assertFalse(client.isPwned("anything"));
        assertEquals("", lastRequestedPath.get(), "Disabled client must not hit the network");
    }

    @Test
    void nullAndBlankReturnFalse() {
        assertFalse(newClient(true).isPwned(null));
        assertFalse(newClient(true).isPwned(""));
    }

    @Test
    void httpErrorFailsOpen() {
        statusToServe.set(503);
        assertFalse(newClient(true).isPwned("any-passphrase-1234"));
    }

    @Test
    void requestUsesFirstFiveHexCharsOfSha1() throws Exception {
        bodyToServe.set("\r\n");
        newClient(true).isPwned("known-input");

        var digest = MessageDigest.getInstance("SHA-1");
        byte[] bytes = digest.digest("known-input".getBytes(StandardCharsets.UTF_8));
        String expectedPrefix =
                HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT).substring(0, 5);
        assertEquals("/range/" + expectedPrefix, lastRequestedPath.get());
    }
}
