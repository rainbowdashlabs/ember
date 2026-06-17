/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import dev.chojo.ember.conf.file.elements.Network;
import io.javalin.http.Context;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the real visitor IP for an incoming Javalin request, honouring
 * forwarded-for headers only from trusted hops.
 *
 * <p>The same app may be deployed in four shapes:
 * <ul>
 *   <li>Direct on a public interface — no proxy, no header trust.</li>
 *   <li>Behind Traefik (or another reverse proxy) — trust
 *       {@code X-Forwarded-For} / {@code X-Real-IP} from the configured CIDRs only.</li>
 *   <li>Behind Cloudflare — trust {@code CF-Connecting-IP}, but only when the
 *       immediate hop is in Cloudflare's published edge ranges.</li>
 *   <li>Behind Cloudflare → Traefik — trust both headers, in that order.</li>
 * </ul>
 *
 * <p>Headers from untrusted hops are ignored. This is essential because any
 * client that can reach the app socket can claim any value in a header — the
 * trust gate is the immediate hop, not the header itself.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>{@code CF-Connecting-IP} — only if {@link Network#cloudflare()} is
 *       {@code true} AND {@link Context#ip()} is a Cloudflare edge address.</li>
 *   <li>Leftmost address in {@code X-Forwarded-For} — only if
 *       {@link Context#ip()} matches one of {@link Network#trustedProxies()}.</li>
 *   <li>{@code X-Real-IP} — same trust check as step 2.</li>
 *   <li>{@link Context#ip()} as a final fallback. This is the correct value
 *       for a no-proxy deployment.</li>
 * </ol>
 *
 * <p>The Cloudflare edge ranges are loaded once at class load from
 * {@code resources/cloudflare-ranges.txt} (generated at build time by the
 * {@code fetchCloudflareRanges} Gradle task) and may be refreshed at runtime
 * via {@link #updateCloudflareRanges(String)} — never via a live HTTP call at
 * request time.
 */
public final class ClientIp {

    private static final String HEADER_CF_CONNECTING_IP = "CF-Connecting-IP";
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";

    private static volatile List<Cidr> cloudflareRanges = loadCloudflareRanges();

    private ClientIp() {}

    /**
     * Resolves the real visitor IP for the given request.
     *
     * @param ctx     the Javalin context
     * @param network the network / proxy configuration
     * @return the visitor's {@link InetAddress}; never {@code null}
     * @throws IllegalStateException if {@code ctx.ip()} cannot be parsed as an
     *                               IP address — should never happen with Javalin
     */
    public static InetAddress resolve(Context ctx, Network network) {
        InetAddress immediateHop = parseOrThrow(ctx.ip());

        if (network.cloudflare() && isCloudflareEdge(immediateHop)) {
            Optional<InetAddress> cf = parseHeader(ctx.header(HEADER_CF_CONNECTING_IP));
            if (cf.isPresent()) return cf.get();
        }

        List<Cidr> trusted = parseTrustedProxies(network.trustedProxies());
        if (!trusted.isEmpty() && matches(immediateHop, trusted)) {
            Optional<InetAddress> xff = parseHeader(leftmost(ctx.header(HEADER_X_FORWARDED_FOR)));
            if (xff.isPresent()) return xff.get();
            Optional<InetAddress> realIp = parseHeader(ctx.header(HEADER_X_REAL_IP));
            if (realIp.isPresent()) return realIp.get();
        }

        return immediateHop;
    }

    static boolean isCloudflareEdge(InetAddress address) {
        return matches(address, cloudflareRanges);
    }

    /**
     * Replaces the in-memory Cloudflare edge range list with the entries parsed
     * from the given text body (one CIDR per line, {@code #} comments ignored).
     * Used by the startup refresh that fetches the latest list from Cloudflare;
     * a no-op (logged at warn level by the caller) if parsing yields nothing,
     * so a transient upstream issue cannot wipe the baseline list shipped with
     * the build.
     *
     * @param content the raw text of {@code ips-v4} concatenated with
     *                {@code ips-v6}, exactly as served by Cloudflare
     * @return the number of CIDR entries that were applied
     */
    public static int updateCloudflareRanges(String content) {
        List<Cidr> parsed = parseRanges(content);
        if (parsed.isEmpty()) return 0;
        cloudflareRanges = parsed;
        return parsed.size();
    }

    /**
     * Returns the number of Cloudflare edge ranges currently loaded. Intended
     * for diagnostic logging and tests.
     */
    public static int cloudflareRangeCount() {
        return cloudflareRanges.size();
    }

    private static boolean matches(InetAddress address, List<Cidr> cidrs) {
        for (Cidr cidr : cidrs) {
            if (cidr.contains(address)) return true;
        }
        return false;
    }

    private static Optional<InetAddress> parseHeader(String value) {
        if (value == null) return Optional.empty();
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        try {
            return Optional.of(InetAddress.getByName(trimmed));
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }

    private static String leftmost(String xff) {
        if (xff == null) return null;
        int comma = xff.indexOf(',');
        return comma == -1 ? xff : xff.substring(0, comma);
    }

    private static InetAddress parseOrThrow(String ip) {
        try {
            return InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("ctx.ip() returned an unparseable value: " + ip, e);
        }
    }

    private static List<Cidr> parseTrustedProxies(List<String> raw) {
        List<Cidr> out = new ArrayList<>(raw.size());
        for (String entry : raw) {
            Cidr.parse(entry).ifPresent(out::add);
        }
        return out;
    }

    private static List<Cidr> loadCloudflareRanges() {
        try (InputStream in = ClientIp.class.getResourceAsStream("/cloudflare-ranges.txt")) {
            if (in == null) return List.of();
            return parseRanges(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read cloudflare-ranges.txt from classpath", e);
        }
    }

    private static List<Cidr> parseRanges(String content) {
        List<Cidr> ranges = new ArrayList<>();
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            Cidr.parse(trimmed).ifPresent(ranges::add);
        }
        return List.copyOf(ranges);
    }

    /**
     * A parsed CIDR block. Supports both IPv4 and IPv6; comparison is done by
     * masking the network bits with {@link BigInteger} so the same path handles
     * both families.
     */
    record Cidr(BigInteger network, BigInteger mask, int family) {

        static Optional<Cidr> parse(String input) {
            String[] parts = input.split("/");
            if (parts.length != 2) return Optional.empty();
            InetAddress address;
            try {
                address = InetAddress.getByName(parts[0]);
            } catch (UnknownHostException e) {
                return Optional.empty();
            }
            int prefix;
            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            int family = address.getAddress().length;
            int bits = family * 8;
            if (prefix < 0 || prefix > bits) return Optional.empty();
            BigInteger ip = new BigInteger(1, address.getAddress());
            BigInteger mask = prefix == 0
                    ? BigInteger.ZERO
                    : BigInteger.ONE
                            .shiftLeft(bits)
                            .subtract(BigInteger.ONE)
                            .shiftLeft(bits - prefix)
                            .and(BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE));
            return Optional.of(new Cidr(ip.and(mask), mask, family));
        }

        boolean contains(InetAddress address) {
            if (address.getAddress().length != family) return false;
            BigInteger ip = new BigInteger(1, address.getAddress());
            return ip.and(mask).equals(network);
        }
    }
}
