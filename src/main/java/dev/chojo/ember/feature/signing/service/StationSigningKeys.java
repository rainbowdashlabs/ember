/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.signing.entity.SealingKey;
import dev.chojo.ember.feature.signing.entity.StoredSigningKey;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Hands out the key a station seals documents with, creating it on first use.
 *
 * <p>The first seal at the installation creates its certificate authority, and the first seal at a
 * station creates that station's key with a certificate issued by the authority (see
 * {@link SigningCertificates}). Private keys are stored only wrapped by {@link SigningKeyWrap}.
 *
 * <p>Two first calls at the same time, in this process or in another one on the same database, both
 * generate a key, but only one is stored: the authority table holds a single row, a station holds at
 * most one active key, and an insert that meets an existing row stores nothing. Each caller then reads
 * back what was stored, so both seal with the same key and the losing one is discarded unseen.
 *
 * <p>The installation is named by the host of the configured base address, which is what readers know
 * it by.
 */
@Singleton
public class StationSigningKeys {
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
     * The key a station seals with, created along with the authority when it has none yet.
     *
     * @param stationId the station
     * @return the station's private key and its chain, station certificate first and authority last
     * @throws IllegalArgumentException when there is no such station
     * @throws SigningKeyWrapException  when a stored key does not open under the at-rest secret
     */
    public SealingKey forStation(int stationId) {
        var authority = authority();
        var stored = keys.findActive(stationId).orElseGet(() -> create(stationId, authority));
        return new SealingKey(
                wrap.unwrap(stored.wrappedPrivateKey()),
                List.of(certificateOf(stored.certificate()), authority.certificate()));
    }

    private StoredSigningKey create(int stationId, SigningCertificates.Issued authority) {
        var station = stations.findById(stationId)
                .orElseThrow(() -> new IllegalArgumentException("No station with id " + stationId));
        var issued = certificates.station(installation, station.name(), station.uid(), authority);
        keys.storeActive(stationId, stored(issued));
        return keys.findActive(stationId)
                .orElseThrow(() -> new IllegalStateException("The station's signing key was not stored"));
    }

    private SigningCertificates.Issued authority() {
        // TODO renew the authority before it expires; station certificates never outlast it
        var stored = keys.findAuthority().orElseGet(this::createAuthority);
        return new SigningCertificates.Issued(
                wrap.unwrap(stored.wrappedPrivateKey()), certificateOf(stored.certificate()));
    }

    private StoredSigningKey createAuthority() {
        keys.storeAuthority(stored(certificates.authority(installation)));
        return keys.findAuthority()
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
}
