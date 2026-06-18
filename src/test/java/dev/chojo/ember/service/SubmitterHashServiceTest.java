/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.form.service.SubmitterHashService;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class SubmitterHashServiceTest {

    private static ApplicationSettingRepository inMemorySettings(Map<String, String> store) {
        var settings = Mockito.mock(ApplicationSettingRepository.class);
        Mockito.when(settings.get(Mockito.anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(0, String.class))));
        Mockito.doAnswer(invocation -> {
                    store.put(invocation.getArgument(0, String.class), invocation.getArgument(1, String.class));
                    return null;
                })
                .when(settings)
                .set(Mockito.anyString(), Mockito.anyString());
        return settings;
    }

    @Test
    void firstCallGeneratesAndPersistsSalt() throws Exception {
        Map<String, String> store = new HashMap<>();
        var service = new SubmitterHashService(inMemorySettings(store));

        byte[] hash = service.hash(InetAddress.getByName("203.0.113.42"), 7);

        assertNotNull(hash);
        assertEquals(32, hash.length);
        // Salt persisted as Base64-encoded 32 bytes.
        String persisted = store.get(SubmitterHashService.SALT_KEY);
        assertNotNull(persisted);
        assertEquals(32, Base64.getDecoder().decode(persisted).length);
    }

    @Test
    void secondCallReusesStoredSalt() throws Exception {
        Map<String, String> store = new HashMap<>();
        var service = new SubmitterHashService(inMemorySettings(store));

        byte[] firstHash = service.hash(InetAddress.getByName("203.0.113.42"), 7);
        String firstSalt = store.get(SubmitterHashService.SALT_KEY);

        byte[] secondHash = service.hash(InetAddress.getByName("203.0.113.42"), 7);
        String secondSalt = store.get(SubmitterHashService.SALT_KEY);

        assertEquals(firstSalt, secondSalt);
        assertArrayEquals(firstHash, secondHash);
    }

    @Test
    void differentFormIdsProduceDifferentHashes() throws Exception {
        var service = new SubmitterHashService(inMemorySettings(new HashMap<>()));
        byte[] hashForFormA = service.hash(InetAddress.getByName("203.0.113.42"), 1);
        byte[] hashForFormB = service.hash(InetAddress.getByName("203.0.113.42"), 2);
        assertFalse(Arrays.equals(hashForFormA, hashForFormB));
    }

    @Test
    void differentClientIpsProduceDifferentHashes() throws Exception {
        var service = new SubmitterHashService(inMemorySettings(new HashMap<>()));
        byte[] hashFromIpA = service.hash(InetAddress.getByName("203.0.113.42"), 1);
        byte[] hashFromIpB = service.hash(InetAddress.getByName("198.51.100.7"), 1);
        assertFalse(Arrays.equals(hashFromIpA, hashFromIpB));
    }

    @Test
    void preExistingSaltIsHonoured() throws Exception {
        Map<String, String> store = new HashMap<>();
        byte[] preExisting = new byte[32];
        for (int i = 0; i < 32; i++) preExisting[i] = (byte) i;
        store.put(SubmitterHashService.SALT_KEY, Base64.getEncoder().encodeToString(preExisting));

        var service = new SubmitterHashService(inMemorySettings(store));
        byte[] hash = service.hash(InetAddress.getByName("203.0.113.42"), 7);

        assertNotNull(hash);
        assertEquals(32, hash.length);
        // Verify the salt was not regenerated.
        assertEquals(Base64.getEncoder().encodeToString(preExisting), store.get(SubmitterHashService.SALT_KEY));
    }

    @Test
    void cachedSaltIsReturnedDirectly() throws Exception {
        Map<String, String> store = new HashMap<>();
        var service = new SubmitterHashService(inMemorySettings(store));
        byte[] firstHash = service.hash(InetAddress.getByName("203.0.113.42"), 7);
        // Wipe the store; the cached salt must keep the service working.
        store.clear();
        byte[] secondHash = service.hash(InetAddress.getByName("203.0.113.42"), 7);
        assertArrayEquals(firstHash, secondHash);
        assertSame(firstHash.getClass(), secondHash.getClass());
    }

    private static void assertArrayEquals(byte[] a, byte[] b) {
        Assertions.assertArrayEquals(a, b);
    }
}
