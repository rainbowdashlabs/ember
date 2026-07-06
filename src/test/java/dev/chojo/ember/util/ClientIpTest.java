/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import dev.chojo.ember.conf.file.elements.Network;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientIpTest {

    private static Network direct() {
        return network(List.of(), false);
    }

    private static Network traefik() {
        return network(List.of("10.0.0.0/8"), false);
    }

    private static Network cloudflareOnly() {
        return network(List.of(), true);
    }

    private static Network cloudflareThenTraefik() {
        return network(List.of("10.0.0.0/8"), true);
    }

    private static Network network(List<String> trustedProxies, boolean cloudflare) {
        Network n = new Network();
        setField(n, "trustedProxies", trustedProxies);
        setField(n, "cloudflare", cloudflare);
        return n;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Context ctx(String ip, String xff, String realIp, String cfConnectingIp) {
        Context ctx = Mockito.mock(Context.class);
        Mockito.when(ctx.ip()).thenReturn(ip);
        Mockito.when(ctx.header("X-Forwarded-For")).thenReturn(xff);
        Mockito.when(ctx.header("X-Real-IP")).thenReturn(realIp);
        Mockito.when(ctx.header("CF-Connecting-IP")).thenReturn(cfConnectingIp);
        return ctx;
    }

    @Test
    void directDeploymentReturnsCtxIp() {
        assertEquals(
                "203.0.113.42",
                ClientIp.resolve(ctx("203.0.113.42", "1.2.3.4", "5.6.7.8", "9.10.11.12"), direct())
                        .getHostAddress());
    }

    @Test
    void traefikTrustsXForwardedForFromTrustedHop() {
        assertEquals(
                "203.0.113.42",
                ClientIp.resolve(ctx("10.0.0.5", "203.0.113.42", null, null), traefik())
                        .getHostAddress());
    }

    @Test
    void traefikTakesRightmostUntrustedFromCommaSeparatedXff() {
        assertEquals(
                "198.51.100.1",
                ClientIp.resolve(ctx("10.0.0.5", "203.0.113.42, 198.51.100.1", null, null), traefik())
                        .getHostAddress());
    }

    @Test
    void traefikSkipsTrustedHopsInChainAndResolvesLeftNeighbour() {
        assertEquals(
                "198.51.100.1",
                ClientIp.resolve(ctx("10.0.0.5", "203.0.113.42, 198.51.100.1, 10.0.0.9", null, null), traefik())
                        .getHostAddress());
    }

    @Test
    void traefikAllTrustedChainResolvesToLeftmostEntry() {
        assertEquals(
                "10.0.0.9",
                ClientIp.resolve(ctx("10.0.0.5", "10.0.0.9, 10.0.0.7", null, null), traefik())
                        .getHostAddress());
    }

    @Test
    void traefikUnparseableChainEntryFallsBackToImmediateHop() {
        assertEquals(
                "10.0.0.5",
                ClientIp.resolve(ctx("10.0.0.5", "not-an-ip, 10.0.0.9", null, null), traefik())
                        .getHostAddress());
    }

    @Test
    void traefikFallsBackToRealIpWhenXffMissing() {
        assertEquals(
                "203.0.113.99",
                ClientIp.resolve(ctx("10.0.0.5", null, "203.0.113.99", null), traefik())
                        .getHostAddress());
    }

    @Test
    void traefikIgnoresXffFromUntrustedHop() {
        assertEquals(
                "8.8.8.8",
                ClientIp.resolve(ctx("8.8.8.8", "203.0.113.42", null, null), traefik())
                        .getHostAddress());
    }

    @Test
    void cloudflareTrustsCfConnectingIpFromCloudflareEdge() {
        assertEquals(
                "203.0.113.42",
                ClientIp.resolve(ctx("104.16.0.1", null, null, "203.0.113.42"), cloudflareOnly())
                        .getHostAddress());
    }

    @Test
    void cloudflareEdgeHopIsTrustedForXffWithoutConfiguredProxies() {
        assertEquals(
                "203.0.113.42",
                ClientIp.resolve(ctx("104.16.0.1", "203.0.113.42", null, null), cloudflareOnly())
                        .getHostAddress());
    }

    @Test
    void cloudflareIgnoresCfConnectingIpFromNonCfHop() {
        assertEquals(
                "8.8.8.8",
                ClientIp.resolve(ctx("8.8.8.8", null, null, "203.0.113.42"), cloudflareOnly())
                        .getHostAddress());
    }

    @Test
    void cloudflareDisabledIgnoresCfConnectingIpEvenFromRealCfHop() {
        assertEquals(
                "104.16.0.1",
                ClientIp.resolve(ctx("104.16.0.1", null, null, "203.0.113.42"), direct())
                        .getHostAddress());
    }

    @Test
    void cloudflareToTraefikChainResolvesViaCfHeader() {
        assertEquals(
                "203.0.113.42",
                ClientIp.resolve(
                                ctx("104.16.0.1", "10.0.0.5, 198.51.100.1", null, "203.0.113.42"),
                                cloudflareThenTraefik())
                        .getHostAddress());
    }

    @Test
    void cloudflareToTraefikResolvesRealClientBehindEdgeFromXffChain() {
        assertEquals(
                "203.0.113.42",
                ClientIp.resolve(
                                ctx("10.0.0.5", "6.6.6.6, 203.0.113.42, 104.16.0.1", null, null),
                                cloudflareThenTraefik())
                        .getHostAddress());
    }

    @Test
    void cloudflareToTraefikIgnoresForgedCfConnectingIpForwardedByTraefik() {
        assertEquals(
                "203.0.113.42",
                ClientIp.resolve(ctx("10.0.0.5", "203.0.113.42", null, "6.6.6.6"), cloudflareThenTraefik())
                        .getHostAddress());
    }

    @Test
    void cloudflareDisabledTreatsEdgeAddressInChainAsOrdinaryClient() {
        assertEquals(
                "104.16.0.1",
                ClientIp.resolve(ctx("10.0.0.5", "203.0.113.42, 104.16.0.1", null, null), traefik())
                        .getHostAddress());
    }

    @Test
    void cloudflareDisabledNoTrustedProxiesIgnoresAllHeaders() {
        assertEquals(
                "203.0.113.7",
                ClientIp.resolve(ctx("203.0.113.7", "1.2.3.4", "5.6.7.8", "9.10.11.12"), direct())
                        .getHostAddress());
    }

    @Test
    void cidrParsesIpv4Slash20() {
        var cidr = ClientIp.Cidr.parse("104.16.0.0/13").orElseThrow();
        try {
            assertTrue(cidr.contains(InetAddress.getByName("104.16.0.1")));
            assertTrue(cidr.contains(InetAddress.getByName("104.23.255.254")));
            assertFalse(cidr.contains(InetAddress.getByName("104.24.0.0")));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void cidrParsesIpv6() {
        var cidr = ClientIp.Cidr.parse("2606:4700::/32").orElseThrow();
        try {
            assertTrue(cidr.contains(InetAddress.getByName("2606:4700::1")));
            assertFalse(cidr.contains(InetAddress.getByName("2606:4701::1")));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void cidrRejectsMalformed() {
        assertTrue(ClientIp.Cidr.parse("not-an-ip/24").isEmpty());
        assertTrue(ClientIp.Cidr.parse("10.0.0.0/notnum").isEmpty());
        assertTrue(ClientIp.Cidr.parse("10.0.0.0/33").isEmpty());
        assertTrue(ClientIp.Cidr.parse("10.0.0.0").isEmpty());
    }
}
