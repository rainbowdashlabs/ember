/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.signing.entity.HeldCopy;
import dev.chojo.ember.feature.signing.entity.PartnerSealCheck;
import dev.chojo.ember.feature.signing.entity.PartnerSealVerdict;
import dev.chojo.ember.feature.signing.entity.SealCheck;
import dev.chojo.ember.feature.signing.entity.SealVerification;
import dev.chojo.ember.feature.signing.entity.SealingPartner;
import dev.chojo.ember.feature.signing.entity.ValidationIndication;
import dev.chojo.ember.feature.signing.entity.ValidationSubIndication;
import dev.chojo.ember.feature.signing.repository.PartnerAuthorityRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;

import static dev.chojo.ember.feature.signing.entity.PartnerSealVerdict.ACCEPTED;
import static dev.chojo.ember.feature.signing.entity.PartnerSealVerdict.ALTERED;
import static dev.chojo.ember.feature.signing.entity.PartnerSealVerdict.NOT_FROM_PARTNER;
import static dev.chojo.ember.feature.signing.entity.PartnerSealVerdict.NOT_VALID;
import static dev.chojo.ember.feature.signing.entity.PartnerSealVerdict.NO_SEAL;
import static dev.chojo.ember.feature.signing.entity.PartnerSealVerdict.REVOKED;
import static dev.chojo.ember.feature.signing.entity.PartnerSealVerdict.UNDECIDED;
import static dev.chojo.ember.feature.signing.entity.PartnerSealVerdict.UNREADABLE;

/**
 * Checks a document a federation partner sent as sealed by that partner, which is what an organiser does
 * with every signed result that comes back from a partner's installation.
 *
 * <p>The check trusts the partner's pinned authorities alone, never this installation's or another
 * partner's, and recognises a seal only when its certificate names the partner station itself. Its own
 * verdict, {@link PartnerSealVerdict}, says whether the document may be taken; the public check of
 * {@link SealVerifier} is not involved and answers as it always did.
 *
 * <p>The partner is asked for its authorities first when nothing is pinned for it yet or the pins are due
 * (a day after it was last asked, or with a revocation list overdue). When a seal is not recognised or cannot be settled, it is asked once
 * more and the document checked again: that is how an authority the partner renewed or re-issued is
 * taken, through its signed statement and nothing else. Asking stays within
 * {@link PartnerAuthorities#BUDGET} each time.
 *
 * <p>A pair a cluster made has no key exchange to bind a statement to; both of its stations run on this
 * installation by definition and seal under its authorities, so those are what a document of such a
 * partner is checked against.
 */
@Singleton
public class PartnerSealValidator {
    private static final List<PartnerSealVerdict> WORST_FIRST =
            List.of(ALTERED, NOT_FROM_PARTNER, REVOKED, NOT_VALID, UNDECIDED, ACCEPTED);
    private static final Set<ValidationIndication> FAILED =
            Set.of(ValidationIndication.TOTAL_FAILED, ValidationIndication.FAILED);
    private static final Set<ValidationIndication> PASSED =
            Set.of(ValidationIndication.TOTAL_PASSED, ValidationIndication.PASSED);
    private static final Set<ValidationSubIndication> CHANGED = Set.of(
            ValidationSubIndication.HASH_FAILURE,
            ValidationSubIndication.SIG_CRYPTO_FAILURE,
            ValidationSubIndication.FORMAT_FAILURE);
    private static final Set<ValidationSubIndication> REVOKED_WITHOUT_PROOF = Set.of(
            ValidationSubIndication.REVOKED,
            ValidationSubIndication.REVOKED_NO_POE,
            ValidationSubIndication.REVOKED_CA_NO_POE);

    private final PartnerAuthorities authorities;
    private final PartnerAuthorityRepository pins;
    private final SealVerifier verifier;

    /**
     * @param authorities asks partners for their authorities and pins them
     * @param pins        the pinned authorities
     * @param verifier    runs the check
     */
    @Inject
    public PartnerSealValidator(
            PartnerAuthorities authorities, PartnerAuthorityRepository pins, SealVerifier verifier) {
        this.authorities = authorities;
        this.pins = pins;
        this.verifier = verifier;
    }

    /**
     * Checks a document a partner sent.
     *
     * @param partner the receiving station's partnership with the station that sealed and sent it
     * @param pdf     the document
     * @return whether it may be taken as sealed by the partner, with what the check found per seal
     */
    public PartnerSealCheck validate(FederationPartner partner, byte[] pdf) {
        var sealer = new SealingPartner(partner.partnerStationId(), partner.partnerStationName());
        if (partner.clusterManaged()) return check(pdf, verifier.installationAuthoritiesOf(sealer), sealer);
        boolean asked = authorities.due(partner.id());
        if (asked) authorities.refresh(partner);
        var checked = check(pdf, pinned(partner, sealer), sealer);
        if (asked || !worthAskingAgain(checked.verdict())) return checked;
        authorities.refresh(partner);
        return check(pdf, pinned(partner, sealer), sealer);
    }

    private List<SealVerifier.Authority> pinned(FederationPartner partner, SealingPartner sealer) {
        return SealVerifier.partnerAuthorities(sealer, pins.pinnedFor(partner.id()));
    }

    private PartnerSealCheck check(byte[] pdf, List<SealVerifier.Authority> trusted, SealingPartner sealer) {
        SealVerification checked;
        try {
            checked = verifier.verifyFromPartner(pdf, trusted);
        } catch (RefusalResponse e) {
            return new PartnerSealCheck(UNREADABLE, new SealVerification(HeldCopy.notHeld(), List.of(), List.of()));
        }
        return new PartnerSealCheck(verdictOf(checked, sealer), checked);
    }

    private static boolean worthAskingAgain(PartnerSealVerdict verdict) {
        return verdict == NOT_FROM_PARTNER || verdict == UNDECIDED;
    }

    private static PartnerSealVerdict verdictOf(SealVerification checked, SealingPartner sealer) {
        if (checked.signatures().isEmpty()) return NO_SEAL;
        var verdicts = checked.signatures().stream()
                .map(seal -> verdictOf(seal, sealer))
                .toList();
        return WORST_FIRST.stream().filter(verdicts::contains).findFirst().orElse(UNDECIDED);
    }

    private static PartnerSealVerdict verdictOf(SealCheck seal, SealingPartner sealer) {
        boolean failed = FAILED.contains(seal.indication());
        var subIndication = seal.subIndication();
        if (!seal.intact() || seal.modifiedAfterSealing()) return ALTERED;
        if (failed && subIndication != null && CHANGED.contains(subIndication)) return ALTERED;
        var partner = seal.partner();
        if (partner == null || !partner.stationUid().equals(sealer.stationUid())) return NOT_FROM_PARTNER;
        if (PASSED.contains(seal.indication())) return ACCEPTED;
        if (subIndication != null && REVOKED_WITHOUT_PROOF.contains(subIndication)) return REVOKED;
        return failed ? NOT_VALID : UNDECIDED;
    }
}
