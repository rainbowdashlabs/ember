/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Federation;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Validates outbound federation / webhook URLs against the public-internet allow
 * policy. Two layers of defence:
 *
 * <ul>
 *   <li>Scheme - only {@code https} is accepted (unless
 *       {@link Federation#allowPrivateHosts()} is enabled).</li>
 *   <li>IP address - the URL host is resolved via {@link InetAddress#getAllByName}
 *       on every check, and rejected if any returned address falls inside the
 *       deny-list of loopback, link-local, private, multicast, or otherwise
 *       reserved ranges (both IPv4 and IPv6).</li>
 * </ul>
 *
 * <p>The validator is consulted twice per remote URL: once at write time, so an
 * admin gets an immediate 400, and once at send time inside
 * {@code FederationHttpClient} / {@code FederationWebhookService} as a soft
 * check that protects against DNS rebinding and against rows that predate the
 * validator. A clustered deployment would still want IP pinning on the TCP
 * socket to close the resolve-vs-connect race.
 */
@Singleton
public class RemoteUrlValidator {
    private static final Logger log = LoggerFactory.getLogger(RemoteUrlValidator.class);
    private static final String SCHEME_HTTPS = "https";
    private static final String REJECT_REASON = "Host must be a public HTTPS endpoint";

    private static final List<Cidr> DENIED_V4 = List.of(
            Cidr.parse4("0.0.0.0/8"),
            Cidr.parse4("10.0.0.0/8"),
            Cidr.parse4("100.64.0.0/10"),
            Cidr.parse4("127.0.0.0/8"),
            Cidr.parse4("169.254.0.0/16"),
            Cidr.parse4("172.16.0.0/12"),
            Cidr.parse4("192.0.0.0/24"),
            Cidr.parse4("192.0.2.0/24"),
            Cidr.parse4("192.168.0.0/16"),
            Cidr.parse4("198.18.0.0/15"),
            Cidr.parse4("198.51.100.0/24"),
            Cidr.parse4("203.0.113.0/24"),
            Cidr.parse4("224.0.0.0/4"),
            Cidr.parse4("240.0.0.0/4"));

    private static final List<Cidr> DENIED_V6 = List.of(
            Cidr.parse6("::/128"),
            Cidr.parse6("::1/128"),
            Cidr.parse6("fc00::/7"),
            Cidr.parse6("fe80::/10"),
            Cidr.parse6("ff00::/8"));

    private final Federation config;
    private final Demo demo;

    @Inject
    public RemoteUrlValidator(Federation config, Demo demo) {
        this.config = config;
        this.demo = demo;
    }

    /**
     * Test-only helper for asserting the rejection reason format.
     */
    public static String rejectReason() {
        return REJECT_REASON;
    }

    private static boolean isDenied(InetAddress address) {
        if (address instanceof Inet4Address v4) {
            return matches(v4.getAddress(), DENIED_V4);
        }
        if (address instanceof Inet6Address v6) {
            byte[] bytes = v6.getAddress();
            Optional<Inet4Address> mapped = mappedIpv4(bytes);
            return mapped.map(inet4Address -> matches(inet4Address.getAddress(), DENIED_V4))
                    .orElseGet(() -> matches(bytes, DENIED_V6));
        }
        return true;
    }

    private static Optional<Inet4Address> mappedIpv4(byte[] v6) {
        for (int i = 0; i < 10; i++) {
            if (v6[i] != 0) return Optional.empty();
        }
        if ((v6[10] & 0xff) != 0xff || (v6[11] & 0xff) != 0xff) return Optional.empty();
        try {
            byte[] v4 = new byte[] {v6[12], v6[13], v6[14], v6[15]};
            return Optional.of((Inet4Address) InetAddress.getByAddress(v4));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static boolean matches(byte[] addressBytes, List<Cidr> cidrs) {
        BigInteger ip = new BigInteger(1, addressBytes);
        for (Cidr cidr : cidrs) {
            if (cidr.contains(ip)) return true;
        }
        return false;
    }

    /**
     * Throws {@link IllegalArgumentException} with a static user-safe message
     * when {@code url} is not an acceptable public HTTPS endpoint.
     */
    public void validate(String url) {
        if (!isAllowed(url)) {
            throw new IllegalArgumentException(REJECT_REASON);
        }
    }

    /**
     * Checks a bare host name (no scheme) against the private-range deny-list. Used
     * for backend endpoints that are not HTTPS URLs - S3 endpoint overrides and
     * SMB/SFTP hosts - so an operator cannot point them at loopback or internal
     * addresses for a port scan. Honours the same {@code allowPrivateHosts} / demo
     * escape hatches as {@link #isAllowed(String)}.
     */
    public boolean isHostAllowed(String host) {
        if (host == null || host.isBlank()) return false;
        if (config.allowPrivateHosts() || demo.dev() || demo.enabled()) return true;

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host.trim());
        } catch (Exception e) {
            log.warn("Refusing host {} - DNS resolution failed: {}", host, e.getMessage());
            return false;
        }
        for (InetAddress address : addresses) {
            if (isDenied(address)) {
                log.warn("Refusing host {} - resolves to denied address {}", host, address);
                return false;
            }
        }
        return true;
    }

    /**
     * Boolean form of {@link #validate(String)} for the soft send-time check.
     */
    public boolean isAllowed(String url) {
        if (url == null || url.isBlank()) return false;
        if (config.allowPrivateHosts() || demo.dev() || demo.enabled()) return true;

        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase(SCHEME_HTTPS)) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) return false;

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (Exception e) {
            log.warn("Refusing federation URL {} - DNS resolution failed: {}", url, e.getMessage());
            return false;
        }
        for (InetAddress address : addresses) {
            if (isDenied(address)) {
                log.warn("Refusing federation URL {} - host resolves to denied address {}", url, address);
                return false;
            }
        }
        return true;
    }

    private record Cidr(BigInteger network, BigInteger mask) {
        static Cidr parse4(String input) {
            return parse(input, 32);
        }

        static Cidr parse6(String input) {
            return parse(input, 128);
        }

        private static Cidr parse(String input, int bits) {
            String[] parts = input.split("/", 2);
            try {
                byte[] addrBytes = InetAddress.getByName(parts[0]).getAddress();
                int prefix = Integer.parseInt(parts[1]);
                BigInteger ip = new BigInteger(1, addrBytes);
                BigInteger mask = prefix == 0
                        ? BigInteger.ZERO
                        : BigInteger.ONE
                                .shiftLeft(bits)
                                .subtract(BigInteger.ONE)
                                .shiftLeft(bits - prefix)
                                .and(BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE));
                return new Cidr(ip.and(mask), mask);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid CIDR in deny-list: " + input, e);
            }
        }

        boolean contains(BigInteger ip) {
            return ip.and(mask).equals(network);
        }
    }
}
