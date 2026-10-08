/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.MemberPermissionResolver;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureFieldName;
import dev.chojo.ember.feature.signing.entity.SignerCapacity;
import dev.chojo.ember.feature.signing.entity.SigningStatements;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Who must sign each signature field of a member's document, read from the field's name and the member's
 * guardians as they stand.
 *
 * <p>The document already carries the guardian rule of its template: "each guardian" became one numbered
 * field per guardian, "one guardian" a single field any of them may sign, and a second guardian's field is
 * missing where the member has only one. So the fields are taken as the document has them, and only the
 * people are looked up: the guardian at each numbered place, the member, the issuer. A member without a
 * login of their own, as most children have none, signs their field through a guardian's account; one with
 * a login may still do so. Every name is the official one.
 */
@Singleton
public class SignerResolver {
    private final StationMemberRepository members;
    private final MemberNameResolver names;
    private final MemberPermissionResolver permissions;

    @Inject
    public SignerResolver(
            StationMemberRepository members, MemberNameResolver names, MemberPermissionResolver permissions) {
        this.members = members;
        this.names = names;
        this.permissions = permissions;
    }

    /**
     * Resolves the signature fields of a document to the people who must sign them.
     *
     * @param member     the member the document is about
     * @param issuerId   the member who issues it, or null where it names nobody
     * @param fieldNames the names of its empty signature fields, in their order
     * @param statements what each kind of signer confirms
     * @return a field to ask for per name the generator writes, in the same order
     */
    public List<RequestedSignature.Draft> resolve(
            StationMember member, @Nullable Integer issuerId, List<String> fieldNames, SigningStatements statements) {
        var guardians = members.findManagers(member.id());
        return fieldNames.stream()
                .flatMap(name -> SignatureFieldName.of(name).stream())
                .map(field -> draft(field, member, guardians, issuerId, statements))
                .toList();
    }

    private RequestedSignature.Draft draft(
            SignatureFieldName field,
            StationMember member,
            List<StationMember> guardians,
            @Nullable Integer issuerId,
            SigningStatements statements) {
        String statement = statements.of(field);
        return switch (field.role()) {
            case PARTICIPANT ->
                new RequestedSignature.Draft(
                        field.name(),
                        field.role(),
                        member.id(),
                        names.official(member.id()),
                        hasLogin(member) ? SignerCapacity.ACCOUNT_HOLDER : SignerCapacity.MEMBER_THROUGH_ACCOUNT,
                        statement);
            case GUARDIAN -> {
                Integer guardian = field.place() <= guardians.size()
                        ? guardians.get(field.place() - 1).id()
                        : null;
                yield named(field, guardian, SignerCapacity.GUARDIAN, statement);
            }
            case ANY_GUARDIAN -> named(field, null, SignerCapacity.GUARDIAN, statement);
            case ISSUER -> named(field, issuerId, SignerCapacity.ACCOUNT_HOLDER, statement);
        };
    }

    /** Whether the member can sign in themselves: an account, and the permission to log in with it. */
    private boolean hasLogin(StationMember member) {
        return member.accountId() != null && permissions.resolve(member).contains(StationPermission.LOGIN);
    }

    private RequestedSignature.Draft named(
            SignatureFieldName field, @Nullable Integer signerId, SignerCapacity capacity, String statement) {
        return new RequestedSignature.Draft(
                field.name(),
                field.role(),
                signerId,
                signerId == null ? null : names.official(signerId),
                capacity,
                statement);
    }
}
