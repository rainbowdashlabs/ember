/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import dev.chojo.ember.conf.file.elements.Network;
import io.javalin.config.ContextResolverConfig;
import io.javalin.http.Context;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the visitor address of a request, trusting forwarding headers only from a trusted immediate hop,
 * since any client reaching the socket can send any header. The hop is always the socket peer, because
 * {@link #installOn} makes {@link Context#ip()} itself answer the resolved address.
 *
 * <ol>
 *   <li>{@code CF-Connecting-IP}, when Cloudflare is enabled and the peer is a Cloudflare edge. Behind
 *       Cloudflare and a proxy the peer is the proxy, which passes that header on from anyone, so it is
 *       not read there.</li>
 *   <li>{@code X-Forwarded-For}, when the peer is a trusted proxy or (with Cloudflare) an edge: walked from
 *       the right, the first address that is not a trusted hop wins, since entries further left are
 *       forgeable. An unparseable entry falls back to the peer; a chain of trusted hops only gives its
 *       leftmost entry.</li>
 *   <li>{@code X-Real-IP}, under the same trust, when there is no {@code X-Forwarded-For}.</li>
 *   <li>The socket peer.</li>
 * </ol>
 *
 * <p>The Cloudflare ranges come from the committed {@code cloudflare-ranges.txt} and may be replaced at
 * runtime through {@link #updateCloudflareRanges(String)}, never fetched at request time.
 */
public final class ClientIp {

    private static final String HEADER_CF_CONNECTING_IP = "CF-Connecting-IP";
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";

    private static volatile List<Cidr> cloudflareRanges = loadCloudflareRanges();

    private ClientIp() {}

    /** Makes {@link Context#ip()} answer the resolved visitor address for every request of the application. */
    public static void installOn(ContextResolverConfig resolver, Network network) {
        resolver.ip = ctx -> resolve(ctx, network).getHostAddress();
    }

    /**
     * The visitor address of this request, never {@code null}.
     *
     * @throws IllegalStateException when the socket peer is not an IP address, which Javalin never gives
     */
    public static InetAddress resolve(Context ctx, Network network) {
        InetAddress immediateHop = parseOrThrow(ctx.req().getRemoteAddr());

        if (network.cloudflare() && isCloudflareEdge(immediateHop)) {
            Optional<InetAddress> cf = parseHeader(ctx.header(HEADER_CF_CONNECTING_IP));
            if (cf.isPresent()) return cf.get();
        }

        List<Cidr> trusted = trustedHops(network);
        if (!trusted.isEmpty() && matches(immediateHop, trusted)) {
            String xff = ctx.header(HEADER_X_FORWARDED_FOR);
            if (xff != null && !xff.isBlank()) {
                return resolveForwardedChain(xff, trusted, immediateHop);
            }
            Optional<InetAddress> realIp = parseHeader(ctx.header(HEADER_X_REAL_IP));
            if (realIp.isPresent()) return realIp.get();
        }

        return immediateHop;
    }

    /**
     * Replaces the Cloudflare ranges with those in the text (one CIDR per line, {@code #} comments), unless
     * it yields none, so a bad upstream answer cannot wipe the shipped list.
     *
     * @return how many ranges were applied
     */
    public static int updateCloudflareRanges(String content) {
        List<Cidr> parsed = parseRanges(content);
        if (parsed.isEmpty()) return 0;
        cloudflareRanges = parsed;
        return parsed.size();
    }

    static boolean isCloudflareEdge(InetAddress address) {
        return matches(address, cloudflareRanges);
    }

    private static boolean matches(InetAddress address, List<Cidr> cidrs) {
        for (Cidr cidr : cidrs) {
            if (cidr.contains(address)) return true;
        }
        return false;
    }

    private static Optional<InetAddress> parseHeader(@Nullable String value) {
        if (value == null) return Optional.empty();
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        try {
            return Optional.of(InetAddress.ofLiteral(trimmed));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static List<Cidr> trustedHops(Network network) {
        List<Cidr> trusted = new ArrayList<>(parseTrustedProxies(network.trustedProxies()));
        if (network.cloudflare()) trusted.addAll(cloudflareRanges);
        return trusted;
    }

    private static InetAddress resolveForwardedChain(String xff, List<Cidr> trusted, InetAddress fallback) {
        String[] entries = xff.split(",");
        InetAddress leftmostTrusted = null;
        for (int i = entries.length - 1; i >= 0; i--) {
            Optional<InetAddress> parsed = parseHeader(entries[i]);
            if (parsed.isEmpty()) return fallback;
            InetAddress address = parsed.get();
            if (!matches(address, trusted)) return address;
            leftmostTrusted = address;
        }
        return leftmostTrusted != null ? leftmostTrusted : fallback;
    }

    private static InetAddress parseOrThrow(String ip) {
        try {
            return InetAddress.ofLiteral(ip);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("The socket peer address is unparseable: " + ip, e);
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

    /** An IPv4 or IPv6 CIDR block, masked as a {@link BigInteger} so both families share one path. */
    record Cidr(BigInteger network, BigInteger mask, int family) {

        static Optional<Cidr> parse(String input) {
            String[] parts = input.split("/");
            if (parts.length != 2) return Optional.empty();
            InetAddress address;
            try {
                address = InetAddress.ofLiteral(parts[0]);
            } catch (IllegalArgumentException e) {
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
