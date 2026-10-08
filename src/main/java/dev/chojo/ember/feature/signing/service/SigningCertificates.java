/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import jakarta.inject.Singleton;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates the key material documents are sealed with: the installation's certificate authority and,
 * issued by it, one certificate per station.
 *
 * <p><b>Algorithm.</b> RSA with 3072-bit keys and SHA-256. RSA is the one signature algorithm every PDF
 * reader validates, older Acrobat and Foxit versions, macOS Preview and the validators built into
 * archive systems included; ECDSA support is common today but still missing from some of them, and a
 * seal that a reader cannot check is worth nothing to that reader. 3072 bits is the size BSI TR-02102-1
 * and NIST SP 800-57 consider sufficient beyond 2030, so neither the authority nor a station key needs
 * a larger size within its lifetime. The cost is a key generation of about a second, paid once per
 * station.
 *
 * <p><b>Validity.</b> The authority holds for {@value #AUTHORITY_YEARS} years: it is the anchor readers
 * pin, and replacing it means every reader has to be told a new fingerprint, so it is meant to outlive
 * many station keys. A station certificate holds for {@value #STATION_YEARS} years, short enough that a
 * key is not in use for a decade and long enough that rotation stays a rare event; it never outlasts
 * the authority that issued it. Both start an hour in the past, so a reader whose clock runs slightly
 * behind does not see a fresh certificate as not yet valid.
 *
 * <p><b>Certificates.</b> The authority is self-signed with a critical {@code basicConstraints} CA flag
 * and the {@code keyCertSign} and {@code cRLSign} usages. A station certificate carries no CA flag and
 * only {@code digitalSignature} and {@code nonRepudiation}, the usages PAdES validators expect of a
 * seal; it carries no extended key usage, since Acrobat refuses one that lacks its own document signing
 * purposes. Its subject names the station (common name, plus the station's id as {@code UID}, which
 * survives a rename) and the installation (organisation). It names the address of its authority's
 * revocation list as its CRL distribution point ({@link RevocationListAddress}), so a reader that
 * fetches revocation data knows where to look. Serial numbers are 159 random bits with the
 * top one set, positive and unique by chance as RFC 5280 recommends.
 */
@Singleton
public class SigningCertificates {
    static final int AUTHORITY_YEARS = 20;
    static final int STATION_YEARS = 5;

    private static final String KEY_ALGORITHM = "RSA";
    private static final int KEY_BITS = 3072;
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final int SERIAL_BITS = 159;
    private static final int MAX_NAME_LENGTH = 64;
    private static final Duration CLOCK_SKEW = Duration.ofHours(1);

    private final SecureRandom random = new SecureRandom();

    /**
     * A private key and the certificate for it.
     *
     * @param privateKey  the private key
     * @param certificate the certificate for its public key
     */
    public record Issued(PrivateKey privateKey, X509Certificate certificate) {}

    /**
     * Creates the installation's certificate authority.
     *
     * @param installation the installation's name, usually the host it is reached at
     * @return the authority's key and self-signed certificate
     */
    public Issued authority(String installation) {
        var keys = newKeyPair();
        var subject = new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.CN, bounded("Ember signing authority " + installation))
                .addRDN(BCStyle.O, bounded(installation))
                .build();
        var notBefore = Instant.now().minus(CLOCK_SKEW);
        var notAfter = plusYears(notBefore, AUTHORITY_YEARS);
        try {
            var extensions = new JcaX509ExtensionUtils();
            var builder = new JcaX509v3CertificateBuilder(
                            subject, newSerial(), Date.from(notBefore), Date.from(notAfter), subject, keys.getPublic())
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
                    .addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign))
                    .addExtension(
                            Extension.subjectKeyIdentifier,
                            false,
                            extensions.createSubjectKeyIdentifier(keys.getPublic()));
            return new Issued(keys.getPrivate(), sign(builder, keys.getPrivate()));
        } catch (GeneralSecurityException | CertIOException e) {
            throw new IllegalStateException("The signing authority could not be created", e);
        }
    }

    /**
     * Creates a station's signing key and its certificate, issued by the authority.
     *
     * @param installation the installation's name, as in the authority
     * @param stationName  the station's name
     * @param stationUid   the station's id, which stays when the station is renamed
     * @param authority    the authority's key and certificate
     * @param revocations  where the authority publishes its revocation list
     * @return the station's key and certificate
     */
    public Issued station(String installation, String stationName, UUID stationUid, Issued authority, URI revocations) {
        var keys = newKeyPair();
        var subject = new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.CN, bounded(stationName))
                .addRDN(BCStyle.UID, stationUid.toString())
                .addRDN(BCStyle.O, bounded(installation))
                .build();
        var issuerCertificate = authority.certificate();
        var now = Instant.now();
        var notBefore = now.minus(CLOCK_SKEW);
        var notAfter =
                earlier(fullStationTermEnd(now), issuerCertificate.getNotAfter().toInstant());
        try {
            var extensions = new JcaX509ExtensionUtils();
            var builder = new JcaX509v3CertificateBuilder(
                            issuerCertificate,
                            newSerial(),
                            Date.from(notBefore),
                            Date.from(notAfter),
                            subject,
                            keys.getPublic())
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(false))
                    .addExtension(
                            Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation))
                    .addExtension(
                            Extension.subjectKeyIdentifier,
                            false,
                            extensions.createSubjectKeyIdentifier(keys.getPublic()))
                    .addExtension(
                            Extension.authorityKeyIdentifier,
                            false,
                            extensions.createAuthorityKeyIdentifier(issuerCertificate))
                    .addExtension(Extension.cRLDistributionPoints, false, distributionPoint(revocations));
            return new Issued(keys.getPrivate(), sign(builder, authority.privateKey()));
        } catch (GeneralSecurityException | CertIOException e) {
            throw new IllegalStateException("The station's signing certificate could not be issued", e);
        }
    }

    /**
     * When a station certificate issued at the given moment expires, unless its authority expires
     * first. An authority that holds at least this long issues a station certificate its full term.
     *
     * @param issuedAt when the certificate is issued
     * @return the end of its full term
     */
    public static Instant fullStationTermEnd(Instant issuedAt) {
        return plusYears(issuedAt.minus(CLOCK_SKEW), STATION_YEARS);
    }

    /**
     * @param certificate a certificate
     * @return its serial number in lower-case hexadecimal, as it is stored
     */
    public static String serialOf(X509Certificate certificate) {
        return certificate.getSerialNumber().toString(16);
    }

    /**
     * Reads a serial number as a reader or an address gives it: hexadecimal in either case, with
     * leading zeros or surrounding blanks.
     *
     * @param text the serial number as given
     * @return it in lower-case hexadecimal, as it is stored, or empty when it is no positive
     *         hexadecimal number
     */
    public static Optional<String> serialNumber(String text) {
        try {
            var serial = new BigInteger(text.strip(), 16);
            return serial.signum() > 0 ? Optional.of(serial.toString(16)) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * @param der a certificate, DER encoded, as it is stored
     * @return the certificate
     */
    public static X509Certificate certificateOf(byte[] der) {
        try {
            return (X509Certificate)
                    CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
        } catch (CertificateException e) {
            throw new IllegalStateException("A stored signing certificate cannot be read", e);
        }
    }

    private static CRLDistPoint distributionPoint(URI revocations) {
        var name = new GeneralNames(new GeneralName(GeneralName.uniformResourceIdentifier, revocations.toString()));
        return new CRLDistPoint(
                new DistributionPoint[] {new DistributionPoint(new DistributionPointName(name), null, null)});
    }

    private static X509Certificate sign(X509v3CertificateBuilder builder, PrivateKey issuerKey)
            throws GeneralSecurityException {
        try {
            var signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(issuerKey);
            return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        } catch (OperatorCreationException e) {
            throw new GeneralSecurityException("No signer for " + SIGNATURE_ALGORITHM, e);
        }
    }

    private KeyPair newKeyPair() {
        try {
            var generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            generator.initialize(KEY_BITS, random);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No " + KEY_ALGORITHM + " key generator", e);
        }
    }

    private BigInteger newSerial() {
        return new BigInteger(SERIAL_BITS - 1, random).setBit(SERIAL_BITS - 1);
    }

    private static Instant plusYears(Instant start, int years) {
        return start.atOffset(ZoneOffset.UTC).plus(Period.ofYears(years)).toInstant();
    }

    private static Instant earlier(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private static String bounded(String name) {
        var trimmed = name.strip();
        if (trimmed.codePointCount(0, trimmed.length()) <= MAX_NAME_LENGTH) return trimmed;
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, MAX_NAME_LENGTH));
    }
}
