/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.ActPictureSource;
import dev.chojo.ember.feature.signing.entity.FieldRole;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.GuardianLink;
import dev.chojo.ember.feature.signing.entity.RecordTimeBasis;
import dev.chojo.ember.feature.signing.entity.SignerCapacity;
import dev.chojo.ember.feature.signing.entity.SigningEvidenceFile;
import dev.chojo.ember.feature.twofactor.entity.CredentialKeyStamp;
import dev.chojo.ember.feature.twofactor.entity.KeyStampKind;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import dev.chojo.ember.util.TypstCompiler;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Renders the signature record page of a signed document: who signed it, for whom, what they confirmed,
 * with which proof, when and from where, which signature picture went into their field and how it was made,
 * which fields are still open or were settled otherwise, and how
 * the seal and its time can be checked. It is the readable alternative to a reader's signature panel,
 * which screen readers, phone viewers and paper do not show.
 *
 * <p>Everything about the acts comes from the {@link SigningEvidenceFile} the document carries as well, so
 * the page and the attachment cannot say different things. Names are the official names the evidence
 * recorded.
 *
 * <p>The page is a Typst template in German and English ({@code signature-record.typ}), written as PDF/A-3b,
 * the format of the documents it is joined to. Typst writes it tagged, with its title and language, so a
 * screen reader can follow it; it cannot claim PDF/UA at the same time as PDF/A, since Typst enforces only
 * one of them per document. Its page numbers continue the document's, so a signer's mark can name the page
 * that holds the record.
 */
public final class SignatureRecordPage {
    private static final String TEMPLATE = "signature-record.typ";
    private static final Pattern GUARDIAN_PLACE = Pattern.compile("\\D*(\\d+)");
    private static final int HASH_GROUP = 4;
    private static final int HASH_GROUPS_PER_LINE = 8;
    private static final int FINGERPRINT_PAIRS_PER_LINE = 8;

    private SignatureRecordPage() {}

    /**
     * What a record page shows beyond the evidence.
     *
     * @param evidence             the evidence of the signing state
     * @param evidenceSha256       SHA-256 of the attached evidence file, lower-case hexadecimal
     * @param stationName          the station the document belongs to
     * @param language             {@code de} or {@code en}
     * @param zone                 the zone the times are shown in
     * @param authorityFingerprint SHA-256 fingerprint of the installation authority that issued the
     *                             sealing certificate, upper-case pairs joined by colons
     * @param timeBasis            where the document's times come from
     * @param verifyAddress        the address of the installation's verification page
     * @param firstPage            the number the record's first page has in the whole document
     */
    public record Input(
            SigningEvidenceFile evidence,
            String evidenceSha256,
            String stationName,
            String language,
            ZoneId zone,
            String authorityFingerprint,
            RecordTimeBasis timeBasis,
            String verifyAddress,
            int firstPage) {}

    /**
     * @param input what the page shows
     * @return the record as a PDF/A-3b document of one or more pages
     * @throws IOException          when Typst fails
     * @throws InterruptedException when the thread is interrupted while Typst runs
     */
    public static byte[] render(Input input) throws IOException, InterruptedException {
        Map<String, Object> data = new HashMap<>();
        data.put("record", model(input));
        return TypstCompiler.compileTemplate(
                data, input.language() + "/" + TEMPLATE, null, Map.of(), Map.of(), TypstCompiler.Output.PDF_A_3B);
    }

    static Model model(Input input) {
        var times = new Times(input.zone(), input.language());
        SigningEvidenceFile evidence = input.evidence();
        List<FieldModel> fields =
                evidence.fields().stream().map(field -> field(field, times)).toList();
        long signed = evidence.fields().stream()
                .filter(field -> field.state() == FieldState.SIGNED)
                .count();
        return new Model(
                input.stationName(),
                evidence.memberName(),
                evidence.requestUid().toString(),
                lines(grouped(evidence.contentSha256()), HASH_GROUPS_PER_LINE),
                times.format(evidence.assembledAt()),
                (int) signed,
                fields.size(),
                fields,
                input.timeBasis(),
                lines(List.of(input.authorityFingerprint().split(":")), FINGERPRINT_PAIRS_PER_LINE, ":"),
                SigningEvidenceFile.FILE_NAME,
                lines(grouped(input.evidenceSha256()), HASH_GROUPS_PER_LINE),
                input.verifyAddress(),
                input.firstPage(),
                withdrawal(evidence.withdrawal(), times));
    }

    private static @Nullable WithdrawalModel withdrawal(
            SigningEvidenceFile.@Nullable Withdrawal withdrawal, Times times) {
        if (withdrawal == null) return null;
        return new WithdrawalModel(
                withdrawal.withdrawnByName(),
                withdrawal.capacity(),
                withdrawal.memberName(),
                withdrawal.reason(),
                times.format(withdrawal.withdrawnAt()),
                withdrawal.truncatedIp(),
                withdrawal.userAgent());
    }

    private static FieldModel field(SigningEvidenceFile.Field field, Times times) {
        SigningEvidenceFile.Act act = field.act();
        return new FieldModel(
                field.role(),
                field.role() == FieldRole.GUARDIAN ? guardianPlace(field.fieldName()) : null,
                field.fieldName(),
                field.state(),
                field.requestedSignerName(),
                field.statement(),
                times.formatNullable(field.settledAt()),
                field.settledByName(),
                act == null ? null : act(act, times));
    }

    private static ActModel act(SigningEvidenceFile.Act act, Times times) {
        SigningEvidenceFile.WebAuthn webAuthn = act.webAuthn();
        return new ActModel(
                act.signerName(),
                act.accountHolderName(),
                act.memberName(),
                act.capacity(),
                guardian(act.guardianLink(), times),
                act.statement(),
                act.entries(),
                act.proof(),
                act.boundToDocument(),
                webAuthn == null ? null : webAuthn.userVerified(),
                webAuthn == null ? null : keyStamp(webAuthn.credentialKeyStamp(), times),
                times.format(act.signedAt()),
                act.truncatedIp(),
                act.userAgent(),
                picture(act.picture()));
    }

    private static @Nullable PictureModel picture(SigningEvidenceFile.@Nullable Picture picture) {
        if (picture == null) return null;
        return new PictureModel(picture.source(), lines(grouped(picture.sha256()), HASH_GROUPS_PER_LINE));
    }

    private static @Nullable GuardianModel guardian(@Nullable GuardianLink link, Times times) {
        if (link == null) return null;
        return new GuardianModel(link.position() + 1, times.formatNullable(link.linkedAt()), link.linkedByName());
    }

    private static KeyStampModel keyStamp(@Nullable CredentialKeyStamp stamp, Times times) {
        if (stamp == null) return new KeyStampModel(false, null, null);
        return new KeyStampModel(true, times.format(stamp.stampedAt()), stamp.kind());
    }

    private static @Nullable Integer guardianPlace(String fieldName) {
        var matcher = GUARDIAN_PLACE.matcher(fieldName);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private static List<String> grouped(String hex) {
        var groups = new ArrayList<String>();
        for (int i = 0; i < hex.length(); i += HASH_GROUP) {
            groups.add(hex.substring(i, Math.min(hex.length(), i + HASH_GROUP)));
        }
        return groups;
    }

    private static List<String> lines(List<String> parts, int perLine) {
        return lines(parts, perLine, " ");
    }

    private static List<String> lines(List<String> parts, int perLine, String delimiter) {
        var lines = new ArrayList<String>();
        for (int i = 0; i < parts.size(); i += perLine) {
            lines.add(String.join(delimiter, parts.subList(i, Math.min(parts.size(), i + perLine))));
        }
        return lines;
    }

    /** Formats instants for the page, in the station's zone and language, with the zone named. */
    private record Times(DateTimeFormatter formatter) {
        Times(ZoneId zone, String language) {
            this(DateTimeFormatter.ofPattern(
                            "en".equals(language) ? "d MMMM yyyy, HH:mm:ss z" : "d. MMMM yyyy, HH:mm:ss z",
                            "en".equals(language) ? Locale.ENGLISH : Locale.GERMAN)
                    .withZone(zone));
        }

        String format(Instant instant) {
            return formatter.format(instant);
        }

        @Nullable
        String formatNullable(@Nullable Instant instant) {
            return instant == null ? null : format(instant);
        }
    }

    /**
     * The data the template reads.
     *
     * @param station              the station's name
     * @param member               the official name of the member the document is about
     * @param requestUid           the request for signatures
     * @param contentSha256        the content hash in lines of grouped characters
     * @param assembledAt          when this signing state was put together
     * @param signed               how many fields are signed
     * @param fieldCount           how many fields the request asks for
     * @param fields               every field
     * @param timeBasis            where the times come from
     * @param authorityFingerprint the authority's fingerprint in lines of pairs
     * @param evidenceFile         the name of the attached evidence file
     * @param evidenceSha256       its hash in lines of grouped characters
     * @param verifyAddress        the installation's verification page
     * @param firstPage            the page number the record starts on
     * @param withdrawal           the withdrawal of the agreement, or null where nobody withdrew it
     */
    record Model(
            String station,
            String member,
            String requestUid,
            List<String> contentSha256,
            String assembledAt,
            int signed,
            int fieldCount,
            List<FieldModel> fields,
            RecordTimeBasis timeBasis,
            List<String> authorityFingerprint,
            String evidenceFile,
            List<String> evidenceSha256,
            String verifyAddress,
            int firstPage,
            @Nullable WithdrawalModel withdrawal) {}

    /**
     * The withdrawal on the page.
     *
     * @param withdrawnByName who withdrew the agreement
     * @param capacity        {@link SignerCapacity#GUARDIAN} where a guardian withdrew it for the member
     * @param memberName      the member a guardian withdrew it for, or null
     * @param reason          why, or null
     * @param withdrawnAt     when
     * @param truncatedIp     from which network, or null
     * @param userAgent       with which browser, or null
     */
    record WithdrawalModel(
            String withdrawnByName,
            SignerCapacity capacity,
            @Nullable String memberName,
            @Nullable String reason,
            String withdrawnAt,
            @Nullable String truncatedIp,
            @Nullable String userAgent) {}

    /**
     * One field on the page.
     *
     * @param role                who signs it
     * @param place               the guardian's place for a numbered guardian field, else null
     * @param fieldName           the field's name in the document
     * @param state               where it stands
     * @param requestedSignerName who was asked to sign it, or null
     * @param statement           what it asks to confirm
     * @param settledAt           when it was settled, or null
     * @param settledByName       who settled it, or null
     * @param act                 the act that filled it, or null
     */
    record FieldModel(
            FieldRole role,
            @Nullable Integer place,
            String fieldName,
            FieldState state,
            @Nullable String requestedSignerName,
            String statement,
            @Nullable String settledAt,
            @Nullable String settledByName,
            @Nullable ActModel act) {}

    /**
     * One act on the page.
     *
     * @param signerName        whose signature it is
     * @param accountHolderName whose account confirmed it
     * @param memberName        the member signed for or signing through the account, or null
     * @param capacity          in what capacity it was given
     * @param guardian          the guardian link it went through, or null
     * @param statement         what was confirmed
     * @param entries           what the signer typed
     * @param proof             what it was confirmed with
     * @param bound             whether the proof signs the content
     * @param userVerified      whether the authenticator verified its user, null for a code or password
     * @param keyStamp          the timestamp of the credential's key, null for a code or password
     * @param signedAt          when
     * @param truncatedIp       from which network, or null
     * @param userAgent         with which browser, or null
     * @param picture           the signature picture the act left in its field, or null where it left none
     */
    record ActModel(
            String signerName,
            String accountHolderName,
            @Nullable String memberName,
            SignerCapacity capacity,
            @Nullable GuardianModel guardian,
            String statement,
            List<SigningEvidenceFile.Entry> entries,
            StepUpProof proof,
            boolean bound,
            @Nullable Boolean userVerified,
            @Nullable KeyStampModel keyStamp,
            String signedAt,
            @Nullable String truncatedIp,
            @Nullable String userAgent,
            @Nullable PictureModel picture) {}

    /**
     * A signature picture on the page.
     *
     * @param source how it came to the act
     * @param sha256 its hash in lines of grouped characters
     */
    record PictureModel(ActPictureSource source, List<String> sha256) {}

    /**
     * A guardian link on the page.
     *
     * @param place        the guardian's place, counted from 1
     * @param linkedAt     when the link was made, or null
     * @param linkedByName who made it, or null
     */
    record GuardianModel(
            int place, @Nullable String linkedAt, @Nullable String linkedByName) {}

    /**
     * A credential's key timestamp on the page.
     *
     * @param stamped   whether the key has a timestamp
     * @param stampedAt the time the timestamp states, or null
     * @param kind      how it was obtained, or null
     */
    record KeyStampModel(
            boolean stamped,
            @Nullable String stampedAt,
            @Nullable KeyStampKind kind) {}
}
