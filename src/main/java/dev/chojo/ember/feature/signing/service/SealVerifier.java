/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.conf.file.elements.Signing;
import dev.chojo.ember.feature.documents.repository.SealedVersionRepository;
import dev.chojo.ember.feature.signing.entity.CertificateFacts;
import dev.chojo.ember.feature.signing.entity.DocumentTimestampCheck;
import dev.chojo.ember.feature.signing.entity.HeldCopy;
import dev.chojo.ember.feature.signing.entity.PadesLevel;
import dev.chojo.ember.feature.signing.entity.PinnedAuthority;
import dev.chojo.ember.feature.signing.entity.RevocationReason;
import dev.chojo.ember.feature.signing.entity.RevocationStatus;
import dev.chojo.ember.feature.signing.entity.SealCheck;
import dev.chojo.ember.feature.signing.entity.SealVerification;
import dev.chojo.ember.feature.signing.entity.SealingPartner;
import dev.chojo.ember.feature.signing.entity.SignerRevocation;
import dev.chojo.ember.feature.signing.entity.StoredAuthorityCertificate;
import dev.chojo.ember.feature.signing.entity.TimestampCheck;
import dev.chojo.ember.feature.signing.entity.ValidationIndication;
import dev.chojo.ember.feature.signing.entity.ValidationSubIndication;
import dev.chojo.ember.feature.signing.repository.PartnerAuthorityRepository;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.util.Sha256;
import eu.europa.esig.dss.diagnostic.AbstractTokenProxy;
import eu.europa.esig.dss.diagnostic.CertificateWrapper;
import eu.europa.esig.dss.diagnostic.PDFRevisionWrapper;
import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import eu.europa.esig.dss.diagnostic.TimestampWrapper;
import eu.europa.esig.dss.diagnostic.jaxb.XmlDigestMatcher;
import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SubIndication;
import eu.europa.esig.dss.enumerations.TimestampType;
import eu.europa.esig.dss.enumerations.TokenExtractionStrategy;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.validation.PDFDocumentValidator;
import eu.europa.esig.dss.pdf.pdfbox.PdfBoxDefaultObjectFactory;
import eu.europa.esig.dss.spi.policy.SignaturePolicyProvider;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.spi.x509.revocation.crl.ExternalResourcesCRLSource;
import eu.europa.esig.dss.validation.reports.Reports;
import io.javalin.http.UploadedFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.security.auth.x500.X500Principal;

/**
 * Checks the seals and timestamps of a PDF somebody holds, against this installation's authorities, and
 * says whether the installation holds exactly that file.
 *
 * <p><b>Trust.</b> The EU DSS validator runs with exactly three kinds of trusted certificates: every
 * authority of this installation, active and retired, every authority pinned for a federation partner of
 * a station here ({@link PartnerAuthorities}), and every timestamp root the installation pins (the
 * shipped ones and the operator's, whether their service is asked today or not). A pinned timestamp
 * root may issue signing certificates for others too, so the validator alone could pass a stranger's
 * seal. DSS has no trust anchor scoped to timestamps short of trusted lists with service types and a
 * policy of its own, and validating signatures without the timestamp roots would drop the proof of
 * existence their timestamps give (a key revoked after a timestamped seal would no longer pass). So the
 * validator runs once, and a signature that is neither issued here nor a partner's never passes: unless
 * the validator found it failed, it reads {@code INDETERMINATE} / {@code NOT_ISSUED_HERE}, with the
 * validator's own verdict beside it.
 *
 * <p><b>Partners.</b> A seal is a partner's only when its chain ends in an authority pinned for a
 * partnership with a station and its certificate names that very station; a partner's authority vouches
 * for no other station of that installation. Such a seal is answered with the partner beside the verdict
 * and never as issued here, so a reader is always told whose seal it is. A document a partner sends is
 * checked by {@link PartnerSealValidator}, against that partner's authorities alone.
 *
 * <p><b>Offline.</b> Nothing is fetched. The verifier is built without online sources and without an
 * AIA source; the revocation data is what the document carries plus the current list of each of the
 * installation's authorities, read from the database (and signed anew there when it is older than a
 * day, as for any reader of the published list), and the last list taken in for each partner authority.
 * A list that cannot be signed because the authority's key does not open is left out, and the signer's
 * revocation status then says unknown.
 *
 * <p><b>What is answered.</b> Small records of what a reader can check themselves: certificates by
 * subject, serial number and fingerprint, the validator's indications, levels and times. Nothing of the
 * validator's own reports leaves this class. Of a document the installation holds, only when it was
 * sealed and to what level: never which station, which document, its title or the people it concerns.
 *
 * <p>The uploaded bytes are only held in memory for the check and never stored.
 *
 * <p><b>Load.</b> Anybody may send a file, and checking one costs far more than sending it, so at most
 * {@link #CONCURRENT_CHECKS} checks run at once on the whole server. A check that finds no free slot within
 * {@link #QUEUE_TIME} is refused as busy. A file the validator fails on in a way it does not refuse itself
 * is answered as no PDF that can be checked, never as a fault of the server.
 */
@Singleton
public class SealVerifier {
    /** The largest file a check takes, in bytes. */
    public static final int MAX_BYTES = 25 * 1024 * 1024;

    /** How far into a file its PDF header may start, as the PDF specification allows. */
    private static final int HEADER_WINDOW = 1024;

    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final String PADES = "PAdES_";
    private static final String PADES_BASELINE = PADES + "BASELINE_";

    private static final Logger log = LoggerFactory.getLogger(SealVerifier.class);

    /** How many checks run at once on the whole server. */
    static final int CONCURRENT_CHECKS = 2;

    /** How long a check waits for one of the others to finish before it is refused. */
    static final Duration QUEUE_TIME = Duration.ofSeconds(5);

    private final SigningKeyRepository keys;
    private final StationKeyRevocations revocations;
    private final SealedVersionRepository versions;
    private final PartnerAuthorityRepository partnerPins;
    private final List<X509Certificate> timestampRoots;
    private final Set<String> timestampFingerprints;
    private final Semaphore slots;
    private final Duration queueTime;

    /**
     * Pins the timestamp roots the configuration names, together with the shipped ones.
     *
     * @param keys        the installation's authorities
     * @param revocations hands out each authority's current revocation list
     * @param versions    the sealed versions of member documents, matched by their SHA-256
     * @param partnerPins the authorities pinned for federation partners
     * @param config      the signing configuration, for the operator's timestamp roots
     */
    @Inject
    public SealVerifier(
            SigningKeyRepository keys,
            StationKeyRevocations revocations,
            SealedVersionRepository versions,
            PartnerAuthorityRepository partnerPins,
            Signing config) {
        this(keys, revocations, versions, partnerPins, TimestampRoots.all(config.timestampRoots()));
    }

    /**
     * Pins the given timestamp roots only, so a test can trust its own timestamp service.
     *
     * @param keys           the installation's authorities
     * @param revocations    hands out each authority's current revocation list
     * @param versions       the sealed versions of member documents, matched by their SHA-256
     * @param partnerPins    the authorities pinned for federation partners
     * @param timestampRoots the roots a timestamp has to chain to
     */
    public SealVerifier(
            SigningKeyRepository keys,
            StationKeyRevocations revocations,
            SealedVersionRepository versions,
            PartnerAuthorityRepository partnerPins,
            List<X509Certificate> timestampRoots) {
        this(keys, revocations, versions, partnerPins, timestampRoots, new Semaphore(CONCURRENT_CHECKS), QUEUE_TIME);
    }

    /**
     * Pins the given timestamp roots and runs checks only while one of the given slots is free.
     *
     * @param keys           the installation's authorities
     * @param revocations    hands out each authority's current revocation list
     * @param versions       the sealed versions of member documents, matched by their SHA-256
     * @param partnerPins    the authorities pinned for federation partners
     * @param timestampRoots the roots a timestamp has to chain to
     * @param slots          the checks that may run at once
     * @param queueTime      how long a check waits for a slot
     */
    SealVerifier(
            SigningKeyRepository keys,
            StationKeyRevocations revocations,
            SealedVersionRepository versions,
            PartnerAuthorityRepository partnerPins,
            List<X509Certificate> timestampRoots,
            Semaphore slots,
            Duration queueTime) {
        this.keys = keys;
        this.revocations = revocations;
        this.versions = versions;
        this.partnerPins = partnerPins;
        this.timestampRoots = List.copyOf(timestampRoots);
        this.timestampFingerprints = fingerprints(this.timestampRoots);
        this.slots = slots;
        this.queueTime = queueTime;
    }

    /**
     * Checks an uploaded file.
     *
     * @param upload the file as it arrived, or null when the request carried none
     * @return what the check found
     * @throws dev.chojo.ember.api.refusal.RefusalResponse when no file came, it is larger than
     *                                                     {@link #MAX_BYTES}, broke off or is no PDF
     */
    public SealVerification verify(@Nullable UploadedFile upload) {
        if (upload == null) throw DocumentRefusal.SEAL_CHECK_NO_FILE.raise();
        if (upload.size() > MAX_BYTES) throw DocumentRefusal.SEAL_CHECK_TOO_LARGE.raise();
        try (var in = upload.content()) {
            return verify(in.readNBytes(MAX_BYTES + 1));
        } catch (IOException e) {
            throw DocumentRefusal.SEAL_CHECK_NOT_RECEIVED.raise();
        }
    }

    /**
     * Checks a file.
     *
     * @param pdf the file's bytes
     * @return what the check found; a PDF without signatures gets two empty lists
     * @throws dev.chojo.ember.api.refusal.RefusalResponse when it is larger than {@link #MAX_BYTES}, is
     *                                                     no PDF that can be read, or no slot for a check
     *                                                     came free in time
     */
    public SealVerification verify(byte[] pdf) {
        if (pdf.length > MAX_BYTES) throw DocumentRefusal.SEAL_CHECK_TOO_LARGE.raise();
        if (!startsLikeAPdf(pdf)) throw DocumentRefusal.SEAL_CHECK_NOT_A_PDF.raise();
        if (!acquireSlot()) throw DocumentRefusal.SEAL_CHECKS_BUSY.raise();
        try {
            var here = installationAuthorities();
            var authorities = new ArrayList<>(here);
            authorities.addAll(partnerAuthorities(partnerPins.allPinned(), fingerprintsOf(here)));
            var checked = checked(pdf, authorities);
            return new SealVerification(held(pdf), checked.signatures(), checked.documentTimestamps());
        } finally {
            slots.release();
        }
    }

    /**
     * Checks a file a federation partner sent, against the given authorities of that partner alone. No slot
     * is taken: the file comes from a partner, not from anybody.
     *
     * @param pdf         the file's bytes
     * @param authorities the partner's authorities, each naming the partner
     * @return what the check found; a seal is recognised only as that partner's
     * @throws dev.chojo.ember.api.refusal.RefusalResponse when it is larger than {@link #MAX_BYTES} or is no
     *                                                     PDF that can be read
     */
    SealVerification verifyFromPartner(byte[] pdf, List<Authority> authorities) {
        if (pdf.length > MAX_BYTES) throw DocumentRefusal.SEAL_CHECK_TOO_LARGE.raise();
        if (!startsLikeAPdf(pdf)) throw DocumentRefusal.SEAL_CHECK_NOT_A_PDF.raise();
        var checked = checked(pdf, authorities);
        return new SealVerification(held(pdf), checked.signatures(), checked.documentTimestamps());
    }

    /**
     * A partner's pinned authorities, as a check of what it sent needs them.
     *
     * @param partner the partner station
     * @param pins    the authorities pinned for the partnership
     * @return one authority per pin, naming the partner
     */
    static List<Authority> partnerAuthorities(SealingPartner partner, List<PinnedAuthority> pins) {
        return pins.stream()
                .map(pin -> new Authority(
                        SigningCertificates.certificateOf(pin.certificate()),
                        PublishedCertificates.fingerprintOf(pin.certificate()),
                        pin.revocationList(),
                        false,
                        List.of(partner)))
                .toList();
    }

    /**
     * This installation's own authorities as a partner's, for a partner on a pair a cluster made, which
     * runs here by definition and seals under the same authorities.
     *
     * @param partner the partner station
     * @return the installation's authorities, naming the partner
     */
    List<Authority> installationAuthoritiesOf(SealingPartner partner) {
        return installationAuthorities().stream()
                .map(authority -> new Authority(
                        authority.certificate(),
                        authority.fingerprint(),
                        authority.listBytes(),
                        true,
                        List.of(partner)))
                .toList();
    }

    /**
     * Every authority pinned for any partnership here, once each, naming every partner station it was pinned
     * for, with the newest revocation list any of them took in. An authority of this installation itself is
     * left out, since it answers as issued here.
     */
    private static List<Authority> partnerAuthorities(List<PinnedAuthority> pins, Set<String> here) {
        var byCertificate = new LinkedHashMap<String, List<PinnedAuthority>>();
        pins.forEach(pin -> byCertificate
                .computeIfAbsent(pin.sha256(), sha256 -> new ArrayList<>())
                .add(pin));
        var authorities = new ArrayList<Authority>();
        for (var same : byCertificate.values()) {
            var der = same.getFirst().certificate();
            var fingerprint = PublishedCertificates.fingerprintOf(der);
            if (here.contains(fingerprint)) continue;
            authorities.add(new Authority(
                    SigningCertificates.certificateOf(der),
                    fingerprint,
                    newestList(same),
                    false,
                    same.stream().map(PinnedAuthority::partner).distinct().toList()));
        }
        return authorities;
    }

    private static byte @Nullable [] newestList(List<PinnedAuthority> pins) {
        return pins.stream()
                .filter(pin -> pin.revocationList() != null)
                .max(Comparator.comparing(
                        PinnedAuthority::revocationNextUpdate, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(PinnedAuthority::revocationList)
                .orElse(null);
    }

    private static Set<String> fingerprintsOf(List<Authority> authorities) {
        return authorities.stream().map(Authority::fingerprint).collect(Collectors.toUnmodifiableSet());
    }

    private boolean acquireSlot() {
        try {
            return slots.tryAcquire(queueTime.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Runs the validator over a file and reads what it found. The validator reads a file nobody vouches
     * for, so anything it fails on that is not one of its own refusals is taken the same way: the file is
     * no PDF that can be checked.
     */
    private Checked checked(byte[] pdf, List<Authority> authorities) {
        try {
            var reports = validate(pdf, authorities);
            var reading = new Reading(pdf.length, reports, authorities, timestampFingerprints);
            return new Checked(
                    reports.getDiagnosticData().getSignatures().stream()
                            .map(reading::sealCheck)
                            .toList(),
                    reports.getDiagnosticData().getTimestampsByType(TimestampType.DOCUMENT_TIMESTAMP).stream()
                            .map(reading::documentTimestampCheck)
                            .toList());
        } catch (RuntimeException e) {
            log.info("A file sent for a seal check could not be checked: {}", e.toString());
            throw DocumentRefusal.SEAL_CHECK_NOT_A_PDF.raise();
        }
    }

    private HeldCopy held(byte[] pdf) {
        return versions.firstWithHash(Sha256.hex(pdf))
                .map(version -> new HeldCopy(true, version.sealedAt(), version.sealLevel()))
                .orElseGet(HeldCopy::notHeld);
    }

    private List<Authority> installationAuthorities() {
        return keys.authorityCertificates().stream().map(this::authority).toList();
    }

    private Authority authority(StoredAuthorityCertificate stored) {
        return new Authority(
                SigningCertificates.certificateOf(stored.certificate()),
                PublishedCertificates.fingerprintOf(stored.certificate()),
                currentList(stored.serialNumber()),
                true,
                List.of());
    }

    private byte @Nullable [] currentList(String serialNumber) {
        try {
            return revocations.revocationList(serialNumber).orElse(null);
        } catch (SigningKeyWrapException e) {
            log.warn(
                    "Checked seals without the current revocation list of authority {}: {}",
                    serialNumber,
                    e.getMessage());
            return null;
        }
    }

    /**
     * Runs the validator over a file, offline, trusting the installation's authorities and the pinned
     * timestamp roots.
     *
     * @param pdf         the file
     * @param authorities the installation's authorities with their current lists
     * @return the validator's reports
     */
    Reports validate(byte[] pdf, List<Authority> authorities) {
        var trusted = new CommonTrustedCertificateSource();
        authorities.forEach(authority -> trusted.addCertificate(new CertificateToken(authority.certificate())));
        timestampRoots.forEach(root -> trusted.addCertificate(new CertificateToken(root)));
        var verifier = new CommonCertificateVerifier(true);
        verifier.setAIASource(null);
        verifier.setTrustedCertSources(trusted);
        var lists = authorities.stream()
                .flatMap(authority -> authority.revocationList().stream())
                .map(InMemoryDocument::new)
                .toArray(DSSDocument[]::new);
        if (lists.length > 0) verifier.setCrlSource(new ExternalResourcesCRLSource(lists));
        var validator = new PDFDocumentValidator(new InMemoryDocument(pdf));
        validator.setPdfObjFactory(new PdfBoxDefaultObjectFactory());
        validator.setCertificateVerifier(verifier);
        validator.setSignaturePolicyProvider(new SignaturePolicyProvider());
        validator.setTokenExtractionStrategy(TokenExtractionStrategy.EXTRACT_CERTIFICATES_ONLY);
        return validator.validateDocument();
    }

    /**
     * What the validator found in a file.
     *
     * @param signatures         one check per signature
     * @param documentTimestamps one check per timestamp over the whole file
     */
    private record Checked(List<SealCheck> signatures, List<DocumentTimestampCheck> documentTimestamps) {}

    private static boolean startsLikeAPdf(byte[] bytes) {
        int last = Math.min(bytes.length - PDF_HEADER.length, HEADER_WINDOW - PDF_HEADER.length);
        for (int start = 0; start <= last; start++) {
            if (Arrays.equals(bytes, start, start + PDF_HEADER.length, PDF_HEADER, 0, PDF_HEADER.length)) return true;
        }
        return false;
    }

    private static Set<String> fingerprints(List<X509Certificate> certificates) {
        return certificates.stream().map(SealVerifier::fingerprintOf).collect(Collectors.toUnmodifiableSet());
    }

    private static String fingerprintOf(X509Certificate certificate) {
        try {
            return PublishedCertificates.fingerprintOf(certificate.getEncoded());
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException("A trusted certificate could not be encoded", e);
        }
    }

    /**
     * An authority a check trusts: one of this installation's or one pinned for federation partners.
     *
     * @param certificate its certificate
     * @param fingerprint the certificate's SHA-256 fingerprint
     * @param listBytes   its current revocation list, DER encoded, or null when none could be had
     * @param here        whether it is one of this installation's authorities
     * @param partners    the partner stations it was pinned for, whose seals under it are recognised as
     *                    theirs; empty for an authority of this installation in the public check
     */
    record Authority(
            X509Certificate certificate,
            String fingerprint,
            byte @Nullable [] listBytes,
            boolean here,
            List<SealingPartner> partners) {

        /** @return its current revocation list, DER encoded, when one could be had */
        Optional<byte[]> revocationList() {
            return Optional.ofNullable(listBytes);
        }

        /**
         * @param signerUid the station the signer's certificate names
         * @return the partner it was pinned for that is that station
         */
        Optional<SealingPartner> partnerNamed(UUID signerUid) {
            return partners.stream()
                    .filter(partner -> partner.stationUid().equals(signerUid))
                    .findFirst();
        }
    }

    /**
     * Turns the validator's reports of one file into the records a reader gets.
     *
     * @param fileLength           the file's length, to tell whether a revision runs to its end
     * @param reports              the validator's reports
     * @param authorities          the authorities the check trusted
     * @param timestampFingerprints the fingerprints of the pinned timestamp roots
     */
    private record Reading(
            long fileLength, Reports reports, List<Authority> authorities, Set<String> timestampFingerprints) {

        SealCheck sealCheck(SignatureWrapper signature) {
            var chain = signature.getCertificateChain();
            var signer = facts(signature.getSigningCertificate());
            var issuer = chain.size() > 1 ? facts(chain.get(1)) : null;
            var issuing = issuer == null ? Optional.<Authority>empty() : authorityBy(issuer.sha256Fingerprint());
            var simple = reports.getSimpleReport();
            var anchor = chain.isEmpty() ? Optional.<Authority>empty() : anchorOf(chain.getLast());
            boolean issuedHere = anchor.map(Authority::here).orElse(false);
            var signerUid = stationUidOf(signature.getSigningCertificate());
            var partner = anchor.flatMap(authority -> signerUid.flatMap(authority::partnerNamed));
            var validatorIndication = indication(simple.getIndication(signature.getId()));
            var validatorSubIndication = subIndication(simple.getSubIndication(signature.getId()));
            boolean answerAsGiven = issuedHere || partner.isPresent() || failed(validatorIndication);
            return new SealCheck(
                    signer,
                    issuer,
                    issuedHere,
                    partner.orElse(null),
                    answerAsGiven ? validatorIndication : ValidationIndication.INDETERMINATE,
                    answerAsGiven ? validatorSubIndication : ValidationSubIndication.NOT_ISSUED_HERE,
                    validatorIndication,
                    validatorSubIndication,
                    level(simple.getSignatureFormat(signature.getId())),
                    instant(signature.getClaimedSigningTime()),
                    intact(signature) && matched(signature.getDigestMatchers()),
                    coversWholeFile(signature.getPDFRevision()),
                    modifiedAfterSealing(signature),
                    signer == null ? SignerRevocation.unknown() : revocation(signer, issuing),
                    signature.getSignatureTimestamps().stream()
                            .map(this::timestampCheck)
                            .toList());
        }

        DocumentTimestampCheck documentTimestampCheck(TimestampWrapper timestamp) {
            return new DocumentTimestampCheck(timestampCheck(timestamp), coversWholeFile(timestamp.getPDFRevision()));
        }

        private TimestampCheck timestampCheck(TimestampWrapper timestamp) {
            var simple = reports.getSimpleReport();
            var detailed = reports.getDetailedReport();
            var indication = simple.getIndication(timestamp.getId());
            var subIndication = simple.getSubIndication(timestamp.getId());
            if (indication == null) {
                indication = detailed.getBasicTimestampValidationIndication(timestamp.getId());
                subIndication = detailed.getBasicTimestampValidationSubIndication(timestamp.getId());
            }
            return new TimestampCheck(
                    instant(timestamp.getProductionTime()),
                    facts(timestamp.getSigningCertificate()),
                    endsIn(timestamp.getCertificateChain(), timestampFingerprints),
                    indication(indication),
                    subIndication(subIndication),
                    intact(timestamp)
                            && timestamp.isMessageImprintDataFound()
                            && timestamp.isMessageImprintDataIntact());
        }

        private SignerRevocation revocation(CertificateFacts signer, Optional<Authority> issuing) {
            var list = issuing.flatMap(Authority::revocationList).map(RevocationLists::read);
            if (list.isEmpty()) return SignerRevocation.unknown();
            var entry = list.get().getRevokedCertificate(new BigInteger(signer.serialNumber(), 16));
            if (entry == null) return SignerRevocation.good();
            var reason = entry.getRevocationReason();
            return new SignerRevocation(
                    RevocationStatus.REVOKED,
                    entry.getRevocationDate().toInstant(),
                    reason == null ? null : reasonOf(reason.name()));
        }

        private Optional<Authority> authorityBy(String fingerprint) {
            return authorities.stream()
                    .filter(authority -> authority.fingerprint().equals(fingerprint))
                    .findFirst();
        }

        private Optional<Authority> anchorOf(CertificateWrapper last) {
            var facts = facts(last);
            return facts == null ? Optional.empty() : authorityBy(facts.sha256Fingerprint());
        }

        private boolean coversWholeFile(@Nullable PDFRevisionWrapper revision) {
            if (revision == null) return false;
            var range = revision.getSignatureByteRange();
            if (range == null || range.size() != 4) return false;
            return range.get(2).add(range.get(3)).longValueExact() == fileLength;
        }
    }

    private static boolean endsIn(List<CertificateWrapper> chain, Set<String> fingerprints) {
        if (chain.isEmpty()) return false;
        var last = facts(chain.getLast());
        return last != null && fingerprints.contains(last.sha256Fingerprint());
    }

    /**
     * Whether a revision after the signed one changes the document beyond what keeps the signature
     * checkable. The validator compares the signed revision with the whole file: pages added or taken
     * away, a visible difference on a page or an annotation over the signed content, and changed objects
     * it files as form fills and signatures, annotations or undefined. Validation material and document
     * timestamps, which it files as extensions, are not a change.
     */
    private static boolean modifiedAfterSealing(SignatureWrapper signature) {
        return signature.arePdfModificationsDetected()
                || !signature.getPdfSignatureOrFormFillChanges().isEmpty()
                || !signature.getPdfAnnotationChanges().isEmpty()
                || !signature.getPdfUndefinedChanges().isEmpty();
    }

    private static boolean intact(AbstractTokenProxy token) {
        return token.isSignatureIntact() && token.isSignatureValid();
    }

    private static boolean matched(List<XmlDigestMatcher> matchers) {
        return !matchers.isEmpty() && matchers.stream().allMatch(m -> m.isDataFound() && m.isDataIntact());
    }

    private static @Nullable CertificateFacts facts(@Nullable CertificateWrapper wrapper) {
        if (wrapper == null) return null;
        var der = wrapper.getBinaries();
        if (der == null) return null;
        var certificate = SigningCertificates.certificateOf(der);
        return new CertificateFacts(
                certificate.getSubjectX500Principal().getName(X500Principal.RFC2253),
                SigningCertificates.serialOf(certificate),
                PublishedCertificates.fingerprintOf(der));
    }

    private static Optional<UUID> stationUidOf(@Nullable CertificateWrapper wrapper) {
        var der = wrapper == null ? null : wrapper.getBinaries();
        if (der == null) return Optional.empty();
        return SigningCertificates.stationUidOf(SigningCertificates.certificateOf(der));
    }

    private static boolean failed(ValidationIndication indication) {
        return indication == ValidationIndication.TOTAL_FAILED || indication == ValidationIndication.FAILED;
    }

    private static ValidationIndication indication(@Nullable Indication indication) {
        return indication == null
                ? ValidationIndication.INDETERMINATE
                : ValidationIndication.valueOf(indication.name());
    }

    private static @Nullable ValidationSubIndication subIndication(@Nullable SubIndication subIndication) {
        return subIndication == null ? null : ValidationSubIndication.valueOf(subIndication.name());
    }

    private static PadesLevel level(@Nullable SignatureLevel level) {
        if (level == null || !level.name().startsWith(PADES_BASELINE)) return PadesLevel.NOT_BASELINE;
        return PadesLevel.valueOf(level.name().substring(PADES.length()));
    }

    private static @Nullable RevocationReason reasonOf(String name) {
        return Arrays.stream(RevocationReason.values())
                .filter(reason -> reason.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private static @Nullable Instant instant(@Nullable Date date) {
        return date == null ? null : date.toInstant();
    }
}
