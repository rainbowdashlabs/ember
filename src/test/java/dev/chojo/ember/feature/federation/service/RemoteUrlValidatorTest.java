/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Federation;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteUrlValidatorTest {

    private static RemoteUrlValidator strict() {
        return new RemoteUrlValidator(new Federation(), new Demo());
    }

    private static RemoteUrlValidator permissive() {
        return new RemoteUrlValidator(
                new Federation() {
                    @Override
                    public boolean allowPrivateHosts() {
                        return true;
                    }
                },
                new Demo());
    }

    private static RemoteUrlValidator demoMode() {
        return new RemoteUrlValidator(new Federation(), new Demo() {
            @Override
            public boolean enabled() {
                return true;
            }
        });
    }

    private static RemoteUrlValidator devMode() {
        return new RemoteUrlValidator(new Federation(), new Demo() {
            @Override
            public boolean dev() {
                return true;
            }
        });
    }

    @Test
    void rejectsHttpScheme() {
        assertFalse(strict().isAllowed("http://example.com"));
    }

    @Test
    void rejectsLoopbackLiteralV4() {
        assertFalse(strict().isAllowed("https://127.0.0.1"));
    }

    @Test
    void rejectsLoopbackLiteralV6() {
        assertFalse(strict().isAllowed("https://[::1]"));
    }

    @Test
    void rejectsPrivateRanges() {
        assertFalse(strict().isAllowed("https://10.0.0.1"));
        assertFalse(strict().isAllowed("https://192.168.1.1"));
        assertFalse(strict().isAllowed("https://172.16.0.1"));
    }

    @Test
    void rejectsLinkLocalAndMetadata() {
        assertFalse(strict().isAllowed("https://169.254.169.254"));
    }

    @Test
    void rejectsIpv6LinkLocal() {
        assertFalse(strict().isAllowed("https://[fe80::1]"));
    }

    @Test
    void rejectsMappedV4Loopback() {
        assertFalse(strict().isAllowed("https://[::ffff:127.0.0.1]"));
    }

    @Test
    void rejectsNullAndBlank() {
        assertFalse(strict().isAllowed(null));
        assertFalse(strict().isAllowed(""));
        assertFalse(strict().isAllowed("   "));
    }

    @Test
    void rejectsLocalhostHostname() {
        assertFalse(strict().isAllowed("https://localhost"));
    }

    @Test
    void permissiveModeAllowsHttpAndLoopback() {
        assertTrue(permissive().isAllowed("http://localhost"));
        assertTrue(permissive().isAllowed("https://127.0.0.1"));
    }

    @Test
    void validateThrowsForPrivateHost() {
        var ex = assertThrows(IllegalArgumentException.class, () -> strict().validate("http://example.com"));
        assertEquals(RemoteUrlValidator.rejectReason(), ex.getMessage());
    }

    @Test
    void validateThrowsForBlank() {
        assertThrows(IllegalArgumentException.class, () -> strict().validate(""));
    }

    @Test
    void demoModeAllowsHttpAndLoopback() {
        assertTrue(demoMode().isAllowed("http://localhost"));
        assertTrue(demoMode().isAllowed("https://127.0.0.1"));
        assertTrue(demoMode().isAllowed("http://192.168.1.1"));
    }

    @Test
    void devModeAllowsHttpAndLoopback() {
        assertTrue(devMode().isAllowed("http://localhost"));
        assertTrue(devMode().isAllowed("https://127.0.0.1"));
        assertTrue(devMode().isAllowed("http://10.0.0.1"));
    }

    @Test
    void demoModeStillRejectsNullAndBlank() {
        assertFalse(demoMode().isAllowed(null));
        assertFalse(demoMode().isAllowed(""));
    }

    @Test
    void strictAllowsPublicHttps() {
        assertTrue(strict().isAllowed("https://example.com"));
    }

    @Test
    void strictRejectsInvalidUri() {
        assertFalse(strict().isAllowed("://not-a-url"));
    }

    @Test
    void strictRejectsNoHost() {
        assertFalse(strict().isAllowed("https://"));
    }

    @Test
    void strictRejectsIpv6UniqueLocal() {
        assertFalse(strict().isAllowed("https://[fc00::1]"));
    }

    @Test
    void strictRejectsMulticast() {
        assertFalse(strict().isAllowed("https://224.0.0.1"));
    }

    @Test
    void strictRejectsIpv6Multicast() {
        assertFalse(strict().isAllowed("https://[ff00::1]"));
    }

    @Test
    void validatePassesForPublicHttps() {
        strict().validate("https://example.com");
    }

    @Test
    void strictRejectsZeroAddressV4() {
        assertFalse(strict().isAllowed("https://0.0.0.1"));
    }

    @Test
    void strictRejectsCgnat() {
        assertFalse(strict().isAllowed("https://100.64.0.1"));
    }

    @Test
    void strictRejectsDocumentation() {
        assertFalse(strict().isAllowed("https://192.0.2.1"));
        assertFalse(strict().isAllowed("https://198.51.100.1"));
        assertFalse(strict().isAllowed("https://203.0.113.1"));
    }

    @Test
    void strictRejectsBenchmark() {
        assertFalse(strict().isAllowed("https://198.18.0.1"));
    }

    @Test
    void strictRejectsReserved() {
        assertFalse(strict().isAllowed("https://240.0.0.1"));
    }

    @Test
    void strictRejectsIpv6Loopback() {
        assertFalse(strict().isAllowed("https://[::1]"));
    }

    @Test
    void strictRejectsIpv6Unspecified() {
        assertFalse(strict().isAllowed("https://[0:0:0:0:0:0:0:0]"));
    }

    @Test
    void strictRejectsMappedV4Private() {
        assertFalse(strict().isAllowed("https://[::ffff:10.0.0.1]"));
    }

    @Test
    void strictRejectsIpv4Ietf() {
        assertFalse(strict().isAllowed("https://192.0.0.1"));
    }

    @Test
    void strictRejectsMappedV4NonLoopback() {
        assertFalse(strict().isAllowed("https://[::ffff:192.168.0.1]"));
    }

    @Test
    void strictAllowsPublicIpv6() {
        assertTrue(strict().isAllowed("https://[2606:4700::1]"));
    }

    @Test
    void strictRejectsDnsFailure() {
        assertFalse(strict().isAllowed("https://this-domain-does-not-exist-982374.invalid"));
    }

    @Test
    void hostAllowedRejectsPrivateAndLoopback() {
        assertFalse(strict().isHostAllowed("127.0.0.1"));
        assertFalse(strict().isHostAllowed("10.0.0.1"));
        assertFalse(strict().isHostAllowed("localhost"));
        assertFalse(strict().isHostAllowed("169.254.169.254"));
    }

    @Test
    void hostAllowedAcceptsPublicHost() {
        assertTrue(strict().isHostAllowed("example.com"));
    }

    @Test
    void hostAllowedRejectsNullBlankAndUnresolvable() {
        assertFalse(strict().isHostAllowed(null));
        assertFalse(strict().isHostAllowed(""));
        assertFalse(strict().isHostAllowed("   "));
        assertFalse(strict().isHostAllowed("this-domain-does-not-exist-982374.invalid"));
    }

    @Test
    void hostAllowedHonoursPermissiveMode() {
        assertTrue(permissive().isHostAllowed("127.0.0.1"));
        assertTrue(demoMode().isHostAllowed("10.0.0.1"));
        assertTrue(devMode().isHostAllowed("localhost"));
    }

    private static boolean isPublic(String address) throws Exception {
        return RemoteUrlValidator.isPublic(InetAddress.getByName(address));
    }

    @Test
    void reservedRangesAreNotPublicAtTheirEdges() throws Exception {
        assertFalse(isPublic("100.64.0.0"));
        assertFalse(isPublic("100.127.255.255"));
        assertTrue(isPublic("100.128.0.1"));
        assertTrue(isPublic("100.63.255.255"));
        assertFalse(isPublic("172.31.255.255"));
        assertTrue(isPublic("172.32.0.1"));
        assertFalse(isPublic("198.19.255.255"));
        assertFalse(isPublic("255.255.255.255"));
        assertFalse(isPublic("fd12:3456::1"));
        assertFalse(isPublic("2001:db8::1"));
        assertFalse(isPublic("fe80::1"));
        assertTrue(isPublic("8.8.8.8"));
        assertTrue(isPublic("2606:4700:4700::1111"));
    }

    @Test
    void ipv6FormsCarryingAPrivateIpv4AddressAreNotPublic() throws Exception {
        assertFalse(isPublic("::ffff:192.168.1.1"));
        assertFalse(isPublic("64:ff9b::a00:1"));
        assertTrue(isPublic("64:ff9b::808:808"));
        assertFalse(isPublic("::7f00:1"));
    }

    @Test
    void aHostResolvingToAPrivateAddressIsRefusedWithoutAskingDnsTwice() throws Exception {
        var lookups = new AtomicInteger();
        OutboundHttp.HostResolver resolver = host -> {
            lookups.incrementAndGet();
            return new InetAddress[] {InetAddress.getByName("8.8.8.8"), InetAddress.getByName("192.168.0.10")};
        };

        var refusal = assertThrows(
                RefusedDestinationException.class, () -> strict().publicAddresses("partner.example", resolver));

        assertEquals(RefusedDestinationException.Reason.NOT_PUBLIC, refusal.reason());
        assertEquals(1, lookups.get());
    }

    @Test
    void aHostThatDoesNotResolveIsRefusedAsSuch() {
        var refusal = assertThrows(
                RefusedDestinationException.class, () -> strict().publicAddresses("nowhere.example", host -> {
                    throw new UnknownHostException(host);
                }));

        assertEquals(RefusedDestinationException.Reason.UNRESOLVABLE, refusal.reason());
    }

    @Test
    void aHostWithOnlyPublicAddressesIsReturnedAsResolved() throws Exception {
        var addresses = strict().publicAddresses(
                        "partner.example", host -> new InetAddress[] {InetAddress.getByName("1.1.1.1")});

        assertEquals(List.of(InetAddress.getByName("1.1.1.1")), addresses);
    }
}
