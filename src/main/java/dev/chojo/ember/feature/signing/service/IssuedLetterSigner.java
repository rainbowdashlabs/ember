/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.generator.service.DocumentGeneratorService.Prepared;
import dev.chojo.ember.feature.generator.service.DocumentGeneratorService.Rendered;
import dev.chojo.ember.feature.generator.service.IssuerSigning;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.signing.entity.IssuerSignature;
import dev.chojo.ember.feature.signing.entity.SignatureMark;
import dev.chojo.ember.feature.signing.repository.IssuerSignatureRepository;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.Sha256;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Signs a generated letter for its issuer, without the issuer signing each one by hand, where they agreed
 * to it once.
 *
 * <p>A letter is signed when all of these hold: it carries the issuer's signature field, its issuer is the
 * one its template names (not one a manager picked for the occasion), that member has an account, and the
 * account both keeps a signature picture and consented to letters being signed with it. Then the picture is
 * drawn into the issuer field with the issuer's official name and the day under it ({@link SignatureMarks}),
 * and the letter is sealed with the station's key ({@link PdfSealer}): with a timestamp and its validation
 * material where the services give them, without a timestamp where no service answers. The signed letter is
 * what is filed and logged, and {@code issuer_signature} records that it was signed, under which consent and
 * with which picture.
 *
 * <p>Where any of it does not hold, the letter is filed as it was drawn: the issuer field stays empty and
 * the letter is not sealed. A letter is sealed only together with the issuer's signature, never on its own,
 * since a seal without the signature it is meant to carry would say the station issued a letter its issuer
 * never signed; the empty field can still be signed by hand. A failure while signing (a station key that does
 * not open, a document that cannot be drawn into) is logged and the letter is filed unsigned the same way,
 * so generating never fails over the signature.
 */
@Singleton
public class IssuedLetterSigner implements IssuerSigning {
    private static final Logger log = LoggerFactory.getLogger(IssuedLetterSigner.class);
    private static final String ISSUER_FIELD = "issuer";

    private final StationMemberRepository members;
    private final StationRepository stations;
    private final SignatureImageService images;
    private final StationSigningKeys keys;
    private final PdfSealer sealer;
    private final IssuerSignatureRepository records;
    private final Clock clock;

    @Inject
    public IssuedLetterSigner(
            StationMemberRepository members,
            StationRepository stations,
            SignatureImageService images,
            StationSigningKeys keys,
            PdfSealer sealer,
            IssuerSignatureRepository records) {
        this(members, stations, images, keys, sealer, records, Clock.systemUTC());
    }

    /**
     * @param clock when a letter is signed, which a test moves
     */
    IssuedLetterSigner(
            StationMemberRepository members,
            StationRepository stations,
            SignatureImageService images,
            StationSigningKeys keys,
            PdfSealer sealer,
            IssuerSignatureRepository records,
            Clock clock) {
        this.members = members;
        this.stations = stations;
        this.images = images;
        this.keys = keys;
        this.sealer = sealer;
        this.records = records;
        this.clock = clock;
    }

    @Override
    public Signed sign(Prepared prepared, Rendered rendered) {
        var consent = consentOf(prepared);
        if (consent.isEmpty()) return Signed.unsigned(rendered);
        try {
            return signed(prepared, rendered, consent.get());
        } catch (RuntimeException e) {
            log.warn(
                    "A letter of station {} could not be signed for its issuer {}; it is filed unsigned",
                    prepared.stationId(),
                    consent.get().issuerId(),
                    e);
            return Signed.unsigned(rendered);
        }
    }

    /** The issuer's standing consent and picture, where the letter is one to sign for them. */
    private Optional<Consent> consentOf(Prepared prepared) {
        var issuer = prepared.issuer();
        Integer issuerId = issuer.memberOfRecord();
        String name = issuer.name();
        if (!issuer.signs() || !issuer.issuer().fixed() || issuerId == null || name == null) return Optional.empty();
        Integer accountId =
                members.findById(issuerId).map(StationMember::accountId).orElse(null);
        if (accountId == null) return Optional.empty();
        var settings = images.settings(accountId);
        Instant consentedAt = settings.autoSignConsentedAt();
        if (consentedAt == null || !settings.hasImage()) return Optional.empty();
        return images.image(accountId).map(png -> new Consent(issuerId, name, consentedAt, png));
    }

    private Signed signed(Prepared prepared, Rendered rendered, Consent consent) {
        Instant now = clock.instant();
        var station = stations.findById(prepared.stationId()).orElse(null);
        var captions = MarkCaptions.of(prepared.source().language().code(), StationFormat.timezoneOf(station));
        var mark = new SignatureMark(ISSUER_FIELD, consent.png(), captions.issued(consent.name(), now));
        byte[] marked = SignatureMarks.draw(rendered.pdf(), List.of(mark));
        var key = keys.forStation(prepared.stationId());
        var sealed = sealer.seal(marked, key.privateKey(), key.chain());
        var record = new IssuerSignature(
                consent.issuerId(),
                consent.consentedAt(),
                Sha256.hex(consent.png()),
                sealed.level(),
                sealed.timestampedBy(),
                now);
        log.info(
                "Letter of station {} signed for its issuer {} and sealed ({})",
                prepared.stationId(),
                consent.issuerId(),
                sealed.level());
        var signedLetter = new Rendered(
                sealed.pdf(), rendered.title(), rendered.fileName(), rendered.resolved(), rendered.unprintable());
        return new Signed(signedLetter, generationId -> records.record(generationId, record));
    }

    /**
     * What a letter is signed with for its issuer.
     *
     * <p>The array is handed over as it is, without a copy.
     *
     * @param issuerId    the issuer
     * @param name        their official name as the letter prints it
     * @param consentedAt when they agreed to letters being signed
     * @param png         their signature picture
     */
    private record Consent(int issuerId, String name, Instant consentedAt, byte[] png) {}
}
