/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.signing.entity.SealingKey;
import dev.chojo.ember.feature.signing.entity.StoredAuthority;
import dev.chojo.ember.feature.signing.entity.StoredSigningKey;
import dev.chojo.ember.feature.signing.entity.StoredStationKey;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Hands out the key a station seals documents with, creating, rotating and renewing as needed.
 *
 * <p>The first seal at the installation creates its certificate authority, and the first seal at a
 * station creates that station's key with a certificate issued by the authority (see
 * {@link SigningCertificates}). Private keys are stored only wrapped by {@link SigningKeyWrap}.
 *
 * <p><b>Rotation.</b> A station key whose certificate has less than {@link #ROTATION_MARGIN} left is
 * replaced on its next use, and {@link #rotate(int)} replaces it at once. The old key is retired, never
 * deleted: documents sealed with it carry its certificate and the authority's, and keep validating
 * through that chain.
 *
 * <p><b>Authority renewal.</b> Before a station certificate is issued, an authority that would end
 * before the certificate's full term ({@link SigningCertificates#fullStationTermEnd(Instant)}) is
 * replaced by a new one, so no station certificate is ever cut short by its issuer. The old authority
 * is retired, never deleted. Station keys it issued stay in use until their own rotation and are
 * always handed out with the authority that issued them, not with the newest one.
 *
 * <p><b>Concurrency.</b> Two callers at the same time, in this process or in another one on the same
 * database, may both generate a key, but only one is stored: at most one authority and one key per
 * station is active, an insert that meets an active row stores nothing, and a replacement retires the
 * old row only while it is still active, in the same transaction that stores the new one. A caller
 * that loses finds the old row retired, stores nothing, and reads back what the winner stored, so all
 * of them seal with the same key and the losing one is discarded unseen.
 *
 * <p>The installation is named by the host of the configured base address, which is what readers know
 * it by.
 */
@Singleton
public class StationSigningKeys {
    /**
     * How long a station certificate must still hold for its key to keep sealing. A seal without a
     * timestamp is checked against the certificate as it stands at the time of checking, so a document
     * sealed in the last days of a certificate stops validating soon after it was handed out. Half a
     * year leaves every newly sealed document that long to be checked, and gives an installation that
     * seals rarely ample room to rotate before the certificate runs out.
     */
    static final Duration ROTATION_MARGIN = Duration.ofDays(180);

    private final SigningKeyRepository keys;
    private final SigningCertificates certificates;
    private final SigningKeyWrap wrap;
    private final StationRepository stations;
    private final String installation;

    /** Names the installation by the host of the configured base address. */
    @Inject
    public StationSigningKeys(
            SigningKeyRepository keys,
            SigningCertificates certificates,
            SigningKeyWrap wrap,
            StationRepository stations,
            Api api) {
        this(keys, certificates, wrap, stations, installationOf(api.baseUrl()));
    }

    /**
     * @param keys         where the keys are stored
     * @param certificates creates the key material
     * @param wrap         wraps private keys for storage
     * @param stations     the stations, for the name in a station certificate
     * @param installation the installation's name in the certificates
     */
    public StationSigningKeys(
            SigningKeyRepository keys,
            SigningCertificates certificates,
            SigningKeyWrap wrap,
            StationRepository stations,
            String installation) {
        this.keys = keys;
        this.certificates = certificates;
        this.wrap = wrap;
        this.stations = stations;
        this.installation = installation;
    }

    /**
     * The key a station seals with: created along with the authority when it has none yet, and rotated
     * when its certificate is close to expiry.
     *
     * @param stationId the station
     * @return the station's private key and its chain, station certificate first and its issuing
     *         authority last
     * @throws IllegalArgumentException when there is no such station
     * @throws SigningKeyWrapException  when a stored key does not open under the at-rest secret
     */
    public SealingKey forStation(int stationId) {
        var active = keys.findActive(stationId);
        var current = active.filter(StationSigningKeys::farFromExpiry).orElseGet(() -> issue(stationId, active));
        return sealingKey(current);
    }

    /**
     * Retires the station's active key and issues a new one. Callers rotating the same key at the same
     * time get one new key between them.
     *
     * @param stationId the station
     * @return the new key and its chain
     * @throws IllegalArgumentException when there is no such station
     * @throws SigningKeyWrapException  when a stored key does not open under the at-rest secret
     */
    public SealingKey rotate(int stationId) {
        return sealingKey(issue(stationId, keys.findActive(stationId)));
    }

    private static boolean farFromExpiry(StoredStationKey key) {
        return key.key().validUntil().isAfter(Instant.now().plus(ROTATION_MARGIN));
    }

    private SealingKey sealingKey(StoredStationKey stored) {
        var authority = keys.findAuthority(stored.authorityId())
                .orElseThrow(() -> new IllegalStateException("The station key's authority is missing"));
        return new SealingKey(
                wrap.unwrap(stored.key().wrappedPrivateKey()),
                List.of(
                        certificateOf(stored.key().certificate()),
                        certificateOf(authority.key().certificate())));
    }

    private StoredStationKey issue(int stationId, Optional<StoredStationKey> replaced) {
        var station = stations.findById(stationId)
                .orElseThrow(() -> new IllegalArgumentException("No station with id " + stationId));
        var authority = activeAuthority();
        var issued = certificates.station(installation, station.name(), station.uid(), authority.issued());
        var fresh = stored(issued);
        Transactions.run(() -> {
            if (replaced.isEmpty() || keys.retire(replaced.get().id())) {
                keys.storeActive(stationId, authority.id(), fresh);
            }
        });
        return keys.findActive(stationId)
                .orElseThrow(() -> new IllegalStateException("The station's signing key was not stored"));
    }

    private Authority activeAuthority() {
        var active = keys.findActiveAuthority();
        var current =
                active.filter(StationSigningKeys::coversAFullStationTerm).orElseGet(() -> replaceAuthority(active));
        return new Authority(
                current.id(),
                new SigningCertificates.Issued(
                        wrap.unwrap(current.key().wrappedPrivateKey()),
                        certificateOf(current.key().certificate())));
    }

    private static boolean coversAFullStationTerm(StoredAuthority authority) {
        return !authority.key().validUntil().isBefore(SigningCertificates.fullStationTermEnd(Instant.now()));
    }

    private StoredAuthority replaceAuthority(Optional<StoredAuthority> replaced) {
        var fresh = stored(certificates.authority(installation));
        Transactions.run(() -> {
            if (replaced.isEmpty() || keys.retireAuthority(replaced.get().id())) keys.storeAuthority(fresh);
        });
        return keys.findActiveAuthority()
                .orElseThrow(() -> new IllegalStateException("The signing authority was not stored"));
    }

    private StoredSigningKey stored(SigningCertificates.Issued issued) {
        var certificate = issued.certificate();
        try {
            return new StoredSigningKey(
                    SigningCertificates.serialOf(certificate),
                    certificate.getEncoded(),
                    wrap.wrap(issued.privateKey()),
                    certificate.getNotAfter().toInstant());
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException("A signing certificate could not be encoded", e);
        }
    }

    private static X509Certificate certificateOf(byte[] der) {
        try {
            return (X509Certificate)
                    CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
        } catch (CertificateException e) {
            throw new IllegalStateException("A stored signing certificate cannot be read", e);
        }
    }

    /** @return the host of a base address, or the address itself when it names no host */
    static String installationOf(String baseUrl) {
        try {
            var host = URI.create(baseUrl).getHost();
            return host == null || host.isBlank() ? baseUrl : host;
        } catch (IllegalArgumentException e) {
            return baseUrl;
        }
    }

    /**
     * The active authority, ready to issue a station certificate.
     *
     * @param id     its row id, recorded with every key it issues
     * @param issued its private key and certificate
     */
    private record Authority(int id, SigningCertificates.Issued issued) {}
}
