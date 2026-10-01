/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.AccessManager;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.inventory.entity.MyInventoryItem;
import dev.chojo.ember.feature.inventory.service.InventoryCheckService;
import dev.chojo.ember.feature.inventory.service.MemberGearService;
import dev.chojo.ember.feature.legal.service.GdprExportService;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldValue;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a guardian sees and changes of the members they look after: the profile, the equipment
 * they hold and need, and the export of their data.
 *
 * <p>Every call names the guardian and the member, and is refused unless the one looks after the
 * other. The profile questions a guardian answers are the ones the member themselves may read.
 */
@Singleton
public class ManagedMemberService {
    private final StationMemberService memberService;
    private final StationMemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final ProfileFieldService profileFieldService;
    private final MemberGearService memberGear;
    private final InventoryCheckService checkService;
    private final GdprExportService gdprExportService;
    private final AccessManager accessManager;

    @Inject
    public ManagedMemberService(
            StationMemberService memberService,
            StationMemberRepository memberRepository,
            AccountRepository accountRepository,
            ProfileFieldService profileFieldService,
            MemberGearService memberGear,
            InventoryCheckService checkService,
            GdprExportService gdprExportService,
            AccessManager accessManager) {
        this.memberService = memberService;
        this.memberRepository = memberRepository;
        this.accountRepository = accountRepository;
        this.profileFieldService = profileFieldService;
        this.memberGear = memberGear;
        this.checkService = checkService;
        this.gdprExportService = gdprExportService;
        this.accessManager = accessManager;
    }

    /**
     * The members a guardian looks after, named and with the address their account carries.
     *
     * @param guardianId the guardian's member id
     * @return the members
     */
    public List<ManagedMember> managed(int guardianId) {
        return memberService.findManaged(guardianId).stream()
                .map(this::toManagedMember)
                .toList();
    }

    private ManagedMember toManagedMember(StationMember m) {
        Integer accountId = m.accountId();
        Account account =
                accountId == null ? null : accountRepository.findById(accountId).orElse(null);
        String name = account != null ? NameParts.of(account).called() : "";
        String email = account != null ? account.email() : "";
        return new ManagedMember(m.id(), m.stationId(), accountId == null ? 0 : accountId, name, email);
    }

    private StationMember requireManaged(int guardianId, int memberId, Refusal missing) {
        boolean manages = memberService.findManaged(guardianId).stream().anyMatch(m -> m.id() == memberId);
        if (!manages) {
            throw MemberRefusal.MEMBER_NOT_YOURS_TO_LOOK_AFTER.raise();
        }
        return memberRepository.findById(memberId).orElseThrow(missing::raise);
    }

    private List<ProfileField> applicableFields(int stationId, int memberId) {
        return profileFieldService.findReadableBy(
                stationId, ProfileFieldScopes.readableBy(accessManager.resolveExpandedMemberPermissions(memberId)));
    }

    /**
     * The profile of a member the guardian looks after.
     *
     * <p>A guardian answers for the station's own questions only. A cluster's questions are asked
     * of the member, and the two id spaces are separate, so origin decides before the id does.
     *
     * @param guardianId the guardian's member id
     * @param memberId   the member
     * @return the questions and the answers
     */
    public MemberProfile profile(int guardianId, int memberId) {
        var member = requireManaged(guardianId, memberId, MemberRefusal.MEMBER_NOT_HERE_ON_MANAGED_PROFILE);
        var fields = applicableFields(member.stationId(), memberId);
        Set<Integer> fieldIds = fields.stream().map(ProfileField::id).collect(Collectors.toSet());
        var values = profileFieldService.findValues(memberId).stream()
                .filter(v -> v.origin() == FieldOrigin.STATION)
                .filter(v -> fieldIds.contains(v.fieldId()))
                .flatMap(
                        v -> Optional.ofNullable(v.value())
                                .map(answer -> new ProfileFieldValue(memberId, v.fieldId(), answer))
                                .stream())
                .toList();
        return new MemberProfile(fields, values);
    }

    /**
     * Answers profile questions for a member the guardian looks after. Answers to questions the
     * member may not read are dropped.
     *
     * @param guardianId the guardian's member id
     * @param memberId   the member
     * @param values     the answers
     * @return the answers as they stand now
     */
    public List<ProfileFieldService.MergedValue> setProfile(int guardianId, int memberId, List<ValueEntry> values) {
        var member = requireManaged(guardianId, memberId, MemberRefusal.MEMBER_NOT_HERE_ON_MANAGED_PROFILE_CHANGE);
        Set<Integer> allowed = applicableFields(member.stationId(), memberId).stream()
                .map(ProfileField::id)
                .collect(Collectors.toSet());
        var entries = values.stream()
                .filter(e -> allowed.contains(e.fieldId()))
                .map(e -> new FieldValueEntry(e.fieldId(), e.value()))
                .toList();
        return profileFieldService.setValues(memberId, entries, guardianId);
    }

    /**
     * The equipment a member the guardian looks after holds, as the member reads it themselves: with
     * the movement each piece is on, its owner and its picture.
     *
     * @param guardianId the guardian's member id
     * @param memberId   the member
     * @return the pieces
     */
    public List<MyInventoryItem> inventory(int guardianId, int memberId) {
        requireManaged(guardianId, memberId, MemberRefusal.MEMBER_NOT_YOURS_TO_LOOK_AFTER);
        return memberGear.heldBy(memberId);
    }

    /**
     * The equipment a member the guardian looks after is expected to hold.
     *
     * @param guardianId the guardian's member id
     * @param memberId   the member
     * @return what is required, per inventory
     */
    public List<MemberRequirement> requirements(int guardianId, int memberId) {
        var member = requireManaged(guardianId, memberId, MemberRefusal.MEMBER_NOT_HERE_ON_MANAGED_EQUIPMENT);
        return checkService.getRequiredItems(member.stationId(), memberId).stream()
                .map(r -> new MemberRequirement(r.inventoryId(), r.inventoryName(), r.requiredQuantity()))
                .toList();
    }

    /**
     * Everything stored about a member the guardian looks after.
     *
     * @param guardianId the guardian's member id
     * @param memberId   the member
     * @return the data and the name to file it under
     */
    public DataExport export(int guardianId, int memberId) {
        requireManaged(guardianId, memberId, MemberRefusal.MEMBER_NOT_YOURS_TO_LOOK_AFTER);
        var data = gdprExportService.exportMemberData(memberId);
        String name = memberRepository
                .findById(memberId)
                .map(StationMember::displayName)
                .orElse("");
        return new DataExport(data, name);
    }

    /**
     * @param data the export
     * @param name the member's name, for the file
     */
    public record DataExport(Map<String, Object> data, String name) {}

    public record MemberRequirement(int inventoryId, String inventoryName, int requiredQuantity) {}

    public record ManagedMember(
            int id,
            int stationId,
            int accountId,
            String name,
            @Nullable String email) {}

    public record MemberProfile(List<ProfileField> fields, List<ProfileFieldValue> values) {}

    public record ValueEntry(int fieldId, String value) {}
}
