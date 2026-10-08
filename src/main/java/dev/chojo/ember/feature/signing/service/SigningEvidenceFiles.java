/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.ActPicture;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureRequestView;
import dev.chojo.ember.feature.signing.entity.SigningAct;
import dev.chojo.ember.feature.signing.entity.SigningEvidence;
import dev.chojo.ember.feature.signing.entity.SigningEvidenceFile;
import dev.chojo.ember.feature.signing.entity.StoredEvidence;
import dev.chojo.ember.util.Json;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns a request for signatures with its evidence into the {@link SigningEvidenceFile} a signed document
 * carries, and that file into its bytes.
 */
public final class SigningEvidenceFiles {
    private SigningEvidenceFiles() {}

    /**
     * The evidence of a request as it stands.
     *
     * @param view        the request, its fields and the evidence of every act on them
     * @param pictures    the signature picture each act left, by the id of the field it filled; an act
     *                    without one is left out
     * @param assembledAt when this signing state is put together
     * @return the evidence file, its fields in the request's order, each with the act that filled it
     * @throws IllegalArgumentException when evidence names a field the request does not have
     */
    public static SigningEvidenceFile of(
            SignatureRequestView view, Map<Integer, ActPicture> pictures, Instant assembledAt) {
        Map<Integer, StoredEvidence> byField =
                view.evidence().stream().collect(Collectors.toMap(StoredEvidence::fieldId, Function.identity()));
        List<SigningEvidenceFile.Field> fields = view.fields().stream()
                .map(field -> field(field, byField.remove(field.id()), pictures.get(field.id())))
                .toList();
        if (!byField.isEmpty()) {
            throw new IllegalArgumentException("Evidence for fields the request does not have: " + byField.keySet());
        }
        var request = view.request();
        return new SigningEvidenceFile(
                SigningEvidenceFile.FORMAT,
                SigningChallenge.LABEL,
                request.uid(),
                request.contentSha256(),
                request.memberName(),
                assembledAt,
                fields);
    }

    /**
     * @param file the evidence file
     * @return the file as UTF-8 encoded JSON
     */
    public static byte[] write(SigningEvidenceFile file) {
        return Json.PRETTY.writeValueAsBytes(file);
    }

    /**
     * @param json a file {@link #write} wrote
     * @return the evidence file it holds
     */
    public static SigningEvidenceFile read(byte[] json) {
        return Json.MAPPER.readValue(json, SigningEvidenceFile.class);
    }

    private static SigningEvidenceFile.Field field(
            RequestedSignature field, @Nullable StoredEvidence evidence, @Nullable ActPicture picture) {
        return new SigningEvidenceFile.Field(
                field.fieldName(),
                field.role(),
                field.state(),
                field.signerName(),
                field.statement(),
                field.settledAt(),
                field.settledByName(),
                evidence == null ? null : act(evidence, picture));
    }

    private static SigningEvidenceFile.Act act(StoredEvidence stored, @Nullable ActPicture picture) {
        SigningEvidence evidence = stored.evidence();
        SigningAct act = evidence.act();
        return new SigningEvidenceFile.Act(
                stored.level(),
                evidence.proof(),
                evidence.boundToDocument(),
                act.signer().capacity(),
                act.signer().accountId(),
                act.signer().memberId(),
                act.accountHolderName(),
                act.memberName(),
                act.signerName(),
                act.fieldName(),
                act.statement(),
                HexFormat.of().formatHex(act.contentSha256()),
                act.entries(),
                act.nonce(),
                act.signedAt(),
                act.truncatedIp(),
                act.userAgent(),
                stored.guardianLink(),
                evidence instanceof SigningEvidence.WebAuthnBound bound ? webAuthn(bound) : null,
                picture == null ? null : new SigningEvidenceFile.Picture(picture.sha256(), picture.source()));
    }

    private static SigningEvidenceFile.WebAuthn webAuthn(SigningEvidence.WebAuthnBound bound) {
        return new SigningEvidenceFile.WebAuthn(
                bound.relyingPartyId(),
                bound.challenge(),
                bound.credentialId(),
                bound.credentialPublicKeyCose(),
                bound.clientDataJson(),
                bound.authenticatorData(),
                bound.signature(),
                bound.userVerified(),
                bound.signatureCount(),
                bound.credentialKeyStamp());
    }
}
