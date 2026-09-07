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
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The second-factor view of the credential store: {@link #getCredentialIdsForUsername(String)}
 * answers only with credentials flagged as second factors, so an assertion after a password
 * offers exactly those and a passkey is never asked for as if it were one.
 *
 * <p>Its own class rather than a flag on {@link WebAuthnCredentialStore}, because the library
 * calls the same method for a registration's exclude list, which must cover every credential the
 * account has. One implementation cannot answer both, which is why there are two relying-party
 * views at all.
 *
 * <p>Note the limit of this filter: for an account whose only credentials are passkeys the list
 * comes back empty, and the library then accepts any credential the account owns. The
 * second-factor finish checks the flag on the verified credential itself; this view only shapes
 * what the browser is asked for.
 */
@Singleton
public class SecondFactorCredentialStore implements CredentialRepository {
    private final TwoFactorRepository repository;
    private final WebAuthnCredentialStore fullStore;

    @Inject
    public SecondFactorCredentialStore(TwoFactorRepository repository, WebAuthnCredentialStore fullStore) {
        this.repository = repository;
        this.fullStore = fullStore;
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        int accountId;
        try {
            accountId = Integer.parseInt(username);
        } catch (NumberFormatException e) {
            return Set.of();
        }
        return repository.findActiveSecondFactorWebAuthnForAccount(accountId).stream()
                .map(c -> PublicKeyCredentialDescriptor.builder()
                        .id(new ByteArray(c.credentialId()))
                        .build())
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return fullStore.getUserHandleForUsername(username);
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return fullStore.getUsernameForUserHandle(userHandle);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return fullStore.lookup(credentialId, userHandle);
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return fullStore.lookupAll(credentialId);
    }
}
