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

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * Decides which outbound destinations this instance may reach: public HTTPS endpoints only, never
 * loopback, private, link-local, carrier-grade NAT, unique-local, multicast, documentation or
 * otherwise reserved addresses, over IPv4 and IPv6 alike.
 *
 * <p>Most of that is what the JDK already knows about an address ({@link InetAddress#isLoopbackAddress},
 * {@link InetAddress#isSiteLocalAddress} and its siblings); the few ranges it has no predicate for
 * are listed in {@link #RESERVED}. An IPv4 address written as an IPv6 one is read back as IPv4 by the
 * JDK itself, and the NAT64 and IPv4-compatible forms are judged by the IPv4 address they carry.
 *
 * <p>Two uses. At write time, {@link #isAllowed(String)} gives an administrator an immediate answer
 * for an address they enter. At send time, {@link OutboundHttp} asks {@link #publicAddresses} for
 * the checked addresses of a host and connects to one of them directly, so a name cannot pass the
 * check with one address and be connected to at another. {@link #isHostAllowed(String)} covers
 * hosts that are not HTTP endpoints at all, such as storage and mail servers.
 *
 * <p>{@code federation.allowPrivateHosts}, demo and development instances switch all of it off, so
 * local setups can federate over plain HTTP and container names.
 */
@Singleton
public class RemoteUrlValidator {
    private static final Logger log = LoggerFactory.getLogger(RemoteUrlValidator.class);
    private static final String SCHEME_HTTPS = "https";
    private static final String REJECT_REASON = "Host must be a public HTTPS endpoint";

    /**
     * The reserved ranges the JDK has no predicate for: "this network", carrier-grade NAT, the IETF
     * protocol block, the documentation and benchmarking nets, the reserved class E block with
     * broadcast, IPv6 unique-local addresses and the IPv6 documentation prefix.
     */
    private static final List<Prefix> RESERVED = List.of(
            Prefix.of("0.0.0.0", 8),
            Prefix.of("100.64.0.0", 10),
            Prefix.of("192.0.0.0", 24),
            Prefix.of("192.0.2.0", 24),
            Prefix.of("198.18.0.0", 15),
            Prefix.of("198.51.100.0", 24),
            Prefix.of("203.0.113.0", 24),
            Prefix.of("240.0.0.0", 4),
            Prefix.of("fc00::", 7),
            Prefix.of("2001:db8::", 32));

    /** IPv6 prefixes whose last four bytes are an IPv4 address: NAT64 and the deprecated compatible form. */
    private static final List<Prefix> EMBEDDING_IPV4 = List.of(Prefix.of("64:ff9b::", 96), Prefix.of("::", 96));

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

    /**
     * Whether an address is one this instance may connect to on somebody else's say-so.
     *
     * @param address a resolved address
     * @return true for a public unicast address
     */
    public static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (RESERVED.stream().anyMatch(prefix -> prefix.contains(bytes))) {
            return false;
        }
        if (address instanceof Inet6Address && EMBEDDING_IPV4.stream().anyMatch(prefix -> prefix.contains(bytes))) {
            return isPublic(embeddedIpv4(bytes));
        }
        return true;
    }

    private static InetAddress embeddedIpv4(byte[] v6) {
        try {
            return InetAddress.getByAddress(Arrays.copyOfRange(v6, 12, 16));
        } catch (IOException e) {
            throw new IllegalStateException("Four bytes are always an IPv4 address", e);
        }
    }

    /**
     * Whether every check is switched off, because the operator allowed private hosts or the instance
     * is a demo or development instance.
     *
     * @return true when private and plain-HTTP destinations are allowed
     */
    public boolean permitsPrivateHosts() {
        return config.allowPrivateHosts() || demo.dev() || demo.enabled();
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
     * Whether a URL is a public HTTPS endpoint, resolving its host to check.
     *
     * <p>A check only: a request sent later resolves again. Requests therefore go through
     * {@link OutboundHttp}, which connects to the address it checked.
     *
     * @param url the URL
     * @return true when it may be called
     */
    public boolean isAllowed(String url) {
        if (url == null || url.isBlank()) return false;
        if (permitsPrivateHosts()) return true;
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
        return isHostAllowed(uri.getHost());
    }

    /**
     * Whether a bare host name (no scheme) resolves to public addresses only. Used on its own for
     * backend endpoints that are not HTTPS URLs - S3 endpoint overrides and SMB, SFTP and IMAP hosts
     * - so they cannot be pointed at loopback or internal addresses for a port scan.
     *
     * @param host the host name or address
     * @return true when it may be reached
     */
    public boolean isHostAllowed(String host) {
        if (host == null || host.isBlank()) return false;
        if (permitsPrivateHosts()) return true;
        try {
            publicAddresses(host.trim(), InetAddress::getAllByName);
            return true;
        } catch (RefusedDestinationException e) {
            log.warn("Refusing host {}: {}", host, e.getMessage());
            return false;
        }
    }

    /**
     * Resolves a host once and returns its addresses, provided every one of them is public.
     *
     * <p>All of them are checked, not just the one that will be used, so a name cannot mix a public
     * address with a private one and hope the private one is picked.
     *
     * @param host     the host name or literal address
     * @param resolver how names are resolved
     * @return the addresses, never empty
     * @throws RefusedDestinationException when the host does not resolve or any address is not public
     */
    public List<InetAddress> publicAddresses(String host, OutboundHttp.HostResolver resolver)
            throws RefusedDestinationException {
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (IOException e) {
            throw new RefusedDestinationException(
                    RefusedDestinationException.Reason.UNRESOLVABLE, "The host " + host + " does not resolve");
        }
        if (addresses == null || addresses.length == 0) {
            throw new RefusedDestinationException(
                    RefusedDestinationException.Reason.UNRESOLVABLE, "The host " + host + " does not resolve");
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new RefusedDestinationException(
                        RefusedDestinationException.Reason.NOT_PUBLIC,
                        "The host " + host + " resolves to the non-public address " + address.getHostAddress());
            }
        }
        return List.of(addresses);
    }

    /**
     * An address prefix, compared byte by byte.
     *
     * @param network the network address
     * @param bits    how many leading bits have to match
     */
    private record Prefix(byte[] network, int bits) {
        static Prefix of(String network, int bits) {
            try {
                return new Prefix(InetAddress.getByName(network).getAddress(), bits);
            } catch (IOException e) {
                throw new IllegalStateException("Invalid reserved network " + network, e);
            }
        }

        boolean contains(byte[] address) {
            if (address.length != network.length) return false;
            int full = bits / 8;
            if (!Arrays.equals(address, 0, full, network, 0, full)) return false;
            int rest = bits % 8;
            if (rest == 0) return true;
            int mask = 0xff << (8 - rest) & 0xff;
            return (address[full] & mask) == (network[full] & mask);
        }
    }
}
