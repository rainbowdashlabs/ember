/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.mapper.wrapper.Row;
import dev.chojo.ember.feature.twofactor.entity.CredentialKeyStamp;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * The evidence of one signing act as it is stored, read back into the evidence the provider handed over.
 *
 * @param id              the evidence identifier
 * @param fieldId         the signature field the act filled
 * @param level           the legal level the signature reached
 * @param evidence        the act and its proof, exactly as recorded
 * @param accountMemberId the member at the station whose account confirmed, or null once they were deleted
 * @param memberId        the member signed for or signing through the account, or null
 * @param guardianLink    the guardian link the act went through, or null where nobody acted as or through a
 *                        guardian
 * @param sealedSha256    SHA-256 of the sealed version the act produced, or null until acts are sealed into
 *                        their field
 * @param recordedAt      when the evidence was stored
 */
public record StoredEvidence(
        int id,
        int fieldId,
        SignatureLevel level,
        SigningEvidence evidence,
        @Nullable Integer accountMemberId,
        @Nullable Integer memberId,
        @Nullable GuardianLink guardianLink,
        @Nullable String sealedSha256,
        Instant recordedAt) {

    /** The evidence columns {@link #map()} reads, behind the alias {@code e}, and the request's uid. */
    public static final String COLUMNS = """
            e.id, e.field_id, e.signature_level, e.proof, e.capacity, e.challenge_account_id, e.challenge_member_id,
            e.account_member_id, e.member_id, e.account_holder_name, e.member_name, e.guardian_position,
            e.guardian_linked_at, e.guardian_linked_by_name, e.field_name, e.statement, e.content_sha256,
            e.entry_fields, e.entry_values, e.nonce, e.signed_at, e.truncated_ip, e.user_agent, e.relying_party_id,
            e.challenge, e.credential_id, e.credential_public_key, e.client_data_json, e.authenticator_data,
            e.signature, e.user_verified, e.signature_count, e.credential_key_stamp_token,
            e.credential_key_stamped_at, e.credential_key_stamp_service, e.credential_key_stamp_kind, e.sealed_sha256,
            e.recorded_at, e.batch_uid, e.batch_position, e.batch_request_uids, e.batch_field_names, e.batch_digests,
            r.uid AS request_uid""";

    /** Maps a row read with {@link #COLUMNS}. */
    public static RowMapping<StoredEvidence> map() {
        return row -> new StoredEvidence(
                row.getInt("id"),
                row.getInt("field_id"),
                row.getEnum("signature_level", SignatureLevel.class),
                evidenceOf(row),
                row.getObject("account_member_id", Integer.class),
                row.getObject("member_id", Integer.class),
                guardianLinkOf(row),
                row.getString("sealed_sha256"),
                row.get("recorded_at", INSTANT_TIMESTAMP));
    }

    private static SigningEvidence evidenceOf(Row row) throws SQLException {
        var act = actOf(row);
        var proof = row.getEnum("proof", StepUpProof.class);
        return switch (proof) {
            case PASSKEY, SECURITY_KEY ->
                new SigningEvidence.WebAuthnBound(
                        act,
                        proof,
                        row.getString("relying_party_id"),
                        row.getBytes("challenge"),
                        row.getBytes("credential_id"),
                        row.getBytes("credential_public_key"),
                        row.getBytes("client_data_json"),
                        row.getBytes("authenticator_data"),
                        row.getBytes("signature"),
                        row.getBoolean("user_verified"),
                        row.getLong("signature_count"),
                        CredentialKeyStamp.read(row, "credential_"));
            case TOTP -> new SigningEvidence.TotpUnbound(act);
            case PASSWORD -> new SigningEvidence.PasswordUnbound(act);
            default -> throw new IllegalStateException("Evidence " + row.getInt("id") + " names the proof " + proof);
        };
    }

    private static SigningAct actOf(Row row) throws SQLException {
        var signer = new Signer(
                row.getEnum("capacity", SignerCapacity.class),
                row.getInt("challenge_account_id"),
                row.getObject("challenge_member_id", Integer.class));
        return new SigningAct(
                row.get("request_uid", UUID_STRING),
                signer,
                row.getString("account_holder_name"),
                row.getString("member_name"),
                row.getString("field_name"),
                row.getString("statement"),
                HexFormat.of().parseHex(row.getString("content_sha256")),
                entriesOf(row),
                row.getBytes("nonce"),
                row.get("signed_at", INSTANT_TIMESTAMP),
                row.getString("truncated_ip"),
                row.getString("user_agent"),
                batchOf(row));
    }

    private static @Nullable BatchMembership batchOf(Row row) throws SQLException {
        UUID uid = row.get("batch_uid", UUID_STRING);
        if (uid == null) return null;
        var requests = (String[]) row.getArray("batch_request_uids").getArray();
        var fields = (String[]) row.getArray("batch_field_names").getArray();
        byte[] digests = row.getBytes("batch_digests");
        int size = BatchMembership.DIGEST_BYTES;
        var items = new ArrayList<BatchMembership.Item>(requests.length);
        for (int i = 0; i < requests.length; i++) {
            items.add(new BatchMembership.Item(
                    UUID.fromString(requests[i]), fields[i], Arrays.copyOfRange(digests, i * size, (i + 1) * size)));
        }
        return new BatchMembership(uid, row.getInt("batch_position"), items);
    }

    private static List<SignerEntry> entriesOf(Row row) throws SQLException {
        var fields = (String[]) row.getArray("entry_fields").getArray();
        var values = (String[]) row.getArray("entry_values").getArray();
        var entries = new ArrayList<SignerEntry>(fields.length);
        for (int i = 0; i < fields.length; i++) {
            entries.add(new SignerEntry(fields[i], values[i]));
        }
        return entries;
    }

    private static @Nullable GuardianLink guardianLinkOf(Row row) throws SQLException {
        Integer position = row.getObject("guardian_position", Integer.class);
        if (position == null) return null;
        return new GuardianLink(
                position, row.get("guardian_linked_at", INSTANT_TIMESTAMP), row.getString("guardian_linked_by_name"));
    }
}
