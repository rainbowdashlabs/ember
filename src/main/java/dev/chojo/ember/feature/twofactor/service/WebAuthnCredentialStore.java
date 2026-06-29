/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import dev.chojo.ember.feature.twofactor.entity.WebAuthnCredential;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bridges Yubico's {@link CredentialRepository} contract to our database. Usernames are stringified
 * account ids — the Yubico API is username-centric but our model identifies users by integer id.
 */
@Singleton
public class WebAuthnCredentialStore implements CredentialRepository {
    private final TwoFactorRepository repository;

    @Inject
    public WebAuthnCredentialStore(TwoFactorRepository repository) {
        this.repository = repository;
    }

    private static RegisteredCredential toRegistered(WebAuthnCredential c) {
        return RegisteredCredential.builder()
                .credentialId(new ByteArray(c.credentialId()))
                .userHandle(new ByteArray(c.userHandle()))
                .publicKeyCose(new ByteArray(c.publicKeyCose()))
                .signatureCount(c.signatureCounter())
                .build();
    }

    private static int parseAccountId(String username) {
        if (username == null) return 0;
        try {
            return Integer.parseInt(username);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        int accountId = parseAccountId(username);
        if (accountId == 0) return Set.of();
        return repository.findActiveWebAuthnForAccount(accountId).stream()
                .map(this::toDescriptor)
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        int accountId = parseAccountId(username);
        if (accountId == 0) return Optional.empty();
        return repository.findUserHandleForAccount(accountId).map(ByteArray::new);
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return repository.findAccountByUserHandle(userHandle.getBytes()).map(String::valueOf);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return repository
                .findWebAuthnByCredentialId(credentialId.getBytes())
                .filter(c -> Arrays.equals(c.userHandle(), userHandle.getBytes()))
                .map(WebAuthnCredentialStore::toRegistered);
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return repository
                .findWebAuthnByCredentialId(credentialId.getBytes())
                .map(WebAuthnCredentialStore::toRegistered)
                .map(Set::of)
                .orElseGet(Set::of);
    }

    private PublicKeyCredentialDescriptor toDescriptor(WebAuthnCredential c) {
        return PublicKeyCredentialDescriptor.builder()
                .id(new ByteArray(c.credentialId()))
                .build();
    }
}
