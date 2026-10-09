/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.signing.entity.SigningAuthorityInfo;
import dev.chojo.ember.feature.signing.entity.StationCertificateInfo;
import dev.chojo.ember.feature.signing.entity.StoredAuthorityCertificate;
import dev.chojo.ember.feature.signing.entity.StoredStationCertificate;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.Sha256;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import javax.security.auth.x500.X500Principal;

/**
 * What the installation publishes so that anybody holding a sealed document can check its seal: the
 * certificates of its signing authorities, their revocation lists, and the certificates of each
 * station's keys.
 *
 * <p>Nothing here creates a key or an authority. A station that never sealed anything has nothing to
 * publish, and asking about it changes nothing. Only certificates are read; the private keys stay in
 * their rows and are never selected on this path.
 *
 * <p>A station's certificates stay published after a rotation or a revocation, since documents sealed
 * with them keep being checked for years. The certificates of a deleted station's keys are no longer
 * reachable by the station, but their authority's certificate and revocation list still are.
 */
@Singleton
public class PublishedCertificates {
    private static final HexFormat FINGERPRINT = HexFormat.ofDelimiter(":").withUpperCase();

    private final SigningKeyRepository keys;
    private final StationKeyRevocations revocations;
    private final StationRepository stations;

    /**
     * @param keys        where the certificates are stored
     * @param revocations hands out the authorities' revocation lists
     * @param stations    resolves a station's public address
     */
    @Inject
    public PublishedCertificates(
            SigningKeyRepository keys, StationKeyRevocations revocations, StationRepository stations) {
        this.keys = keys;
        this.revocations = revocations;
        this.stations = stations;
    }

    /** @return every signing authority of the installation, active and retired, newest first */
    public List<SigningAuthorityInfo> authorities() {
        return keys.authorityCertificates().stream()
                .map(PublishedCertificates::authorityInfo)
                .toList();
    }

    /**
     * @param serialNumber the authority certificate's serial number, hexadecimal
     * @return that authority's certificate, DER encoded, or empty when there is no such authority
     */
    public Optional<byte[]> authorityCertificate(String serialNumber) {
        return SigningCertificates.serialNumber(serialNumber).flatMap(keys::authorityCertificate);
    }

    /**
     * @param serialNumber the authority certificate's serial number, hexadecimal
     * @return that authority's current revocation list, DER encoded, or empty when there is no such
     *         authority
     * @throws SigningKeyWrapException when the authority's key does not open under the at-rest secret
     */
    public Optional<byte[]> revocationList(String serialNumber) {
        return revocations.revocationList(serialNumber);
    }

    /**
     * The station a public address names, provided it has ever had a signing key.
     *
     * @param address the station's uid or public slug
     * @return the station's id
     */
    public int resolveSealingStation(String address) {
        return stations.resolveAddressedId(address)
                .filter(stationId -> !keys.stationCertificates(stationId).isEmpty())
                .orElseThrow(DocumentRefusal.SEALING_STATION_NOT_HERE::raise);
    }

    /**
     * @param stationId the station
     * @return every certificate its keys had, active, retired and revoked, newest first; empty for a
     *         station that never sealed anything
     */
    public List<StationCertificateInfo> stationCertificates(int stationId) {
        return keys.stationCertificates(stationId).stream()
                .map(PublishedCertificates::stationInfo)
                .toList();
    }

    /**
     * @param stationId    the station
     * @param serialNumber the certificate's serial number, hexadecimal
     * @return the station's certificate with that serial number, DER encoded, or empty when its keys
     *         never had it
     */
    public Optional<byte[]> stationCertificate(int stationId, String serialNumber) {
        return SigningCertificates.serialNumber(serialNumber)
                .flatMap(serial -> keys.stationCertificate(stationId, serial));
    }

    /**
     * The authorities that issued a station's certificates, which are what a reader of its documents
     * pins. Usually one; more after the installation renewed its authority.
     *
     * @param stationId the station
     * @return those authorities, the one of the newest certificate first; empty for a station that
     *         never sealed anything
     */
    public List<SigningAuthorityInfo> authoritiesOfStation(int stationId) {
        var issuers = new LinkedHashSet<String>();
        keys.stationCertificates(stationId).forEach(certificate -> issuers.add(certificate.authoritySerialNumber()));
        var authorities = keys.authorityCertificates();
        return issuers.stream()
                .flatMap(serial -> authorities.stream()
                        .filter(authority -> authority.serialNumber().equals(serial))
                        .map(PublishedCertificates::authorityInfo))
                .toList();
    }

    private static SigningAuthorityInfo authorityInfo(StoredAuthorityCertificate stored) {
        var certificate = SigningCertificates.certificateOf(stored.certificate());
        return new SigningAuthorityInfo(
                stored.serialNumber(),
                subjectOf(certificate),
                fingerprintOf(stored.certificate()),
                certificate.getNotBefore().toInstant(),
                certificate.getNotAfter().toInstant(),
                stored.active());
    }

    private static StationCertificateInfo stationInfo(StoredStationCertificate stored) {
        var certificate = SigningCertificates.certificateOf(stored.certificate());
        return new StationCertificateInfo(
                stored.serialNumber(),
                subjectOf(certificate),
                fingerprintOf(stored.certificate()),
                certificate.getNotBefore().toInstant(),
                certificate.getNotAfter().toInstant(),
                stored.authoritySerialNumber(),
                stored.revokedAt());
    }

    private static String subjectOf(X509Certificate certificate) {
        return certificate.getSubjectX500Principal().getName(X500Principal.RFC2253);
    }

    /**
     * @param der a certificate, DER encoded
     * @return its SHA-256 fingerprint the way certificate viewers and {@code openssl x509 -fingerprint}
     *         print it: upper-case hexadecimal pairs separated by colons
     */
    static String fingerprintOf(byte[] der) {
        return FINGERPRINT.formatHex(Sha256.digest().digest(der));
    }
}
