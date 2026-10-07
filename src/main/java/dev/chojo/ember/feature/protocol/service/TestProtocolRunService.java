/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.TestProtocolRefusal;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.service.PrivateTags;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRun;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Starting a test run with the members it is for, and handing a finished run out as files.
 */
@Singleton
public class TestProtocolRunService {
    private static final Logger log = LoggerFactory.getLogger(TestProtocolRunService.class);

    private final TestProtocolService protocols;
    private final TestProtocolPdfService pdfs;
    private final StationMemberRepository members;
    private final MemberGroupRepository groups;
    private final UserTagRepository tags;
    private final AccountRepository accounts;
    private final PrivateTags privateTags;

    @Inject
    public TestProtocolRunService(
            TestProtocolService protocols,
            TestProtocolPdfService pdfs,
            StationMemberRepository members,
            MemberGroupRepository groups,
            UserTagRepository tags,
            AccountRepository accounts,
            PrivateTags privateTags) {
        this.protocols = protocols;
        this.pdfs = pdfs;
        this.members = members;
        this.groups = groups;
        this.tags = tags;
        this.accounts = accounts;
        this.privateTags = privateTags;
    }

    /**
     * Starts a run of a protocol and fills it with the members the request names, directly or
     * through a kind of member, a group or a tag.
     *
     * <p>A group or tag of another station resolves to members of that station, so the run is
     * filled only from the members of the station starting it, whatever was asked for.
     *
     * @param protocolId the protocol, already checked to belong to the station
     * @param stationId  the station starting the run
     * @param createdBy  the member starting it
     */
    public TestProtocolRun start(int protocolId, int stationId, int createdBy, ProtocolRunRequest request) {
        privateTags.requireChoosable(orEmpty(request.tagIds()));
        var run = protocols.createRun(
                protocolId,
                stationId,
                request.name(),
                Objects.requireNonNullElseGet(request.testDate(), LocalDate::now),
                createdBy);
        var named = new LinkedHashSet<Integer>();
        named.addAll(orEmpty(request.memberIds()));
        for (String userType : orEmpty(request.userTypes())) {
            addIds(named, members.findByStationAndUserType(stationId, StationUserType.valueOf(userType)));
        }
        for (int groupId : orEmpty(request.groupIds())) addIds(named, groups.findMembers(groupId));
        for (int tagId : orEmpty(request.tagIds())) addIds(named, tags.findMembers(tagId));
        named.retainAll(stationMemberIds(stationId));
        if (!named.isEmpty()) protocols.addRunMembers(run.id(), new ArrayList<>(named));
        return run;
    }

    private static <T> List<T> orEmpty(@Nullable List<T> list) {
        return list == null ? List.of() : list;
    }

    private static void addIds(Set<Integer> ids, List<StationMember> found) {
        found.forEach(member -> ids.add(member.id()));
    }

    private Set<Integer> stationMemberIds(int stationId) {
        return members.findByStation(stationId).stream().map(StationMember::id).collect(Collectors.toSet());
    }

    /**
     * What a member's sheet is called: their display name, or their official name where they have
     * none, stripped to what a file name may carry.
     */
    public String memberFileName(int memberId) {
        return members.findById(memberId)
                .map(member -> fileName(member, memberId))
                .orElse("Member_" + memberId);
    }

    private String fileName(StationMember member, int memberId) {
        String name = member.displayName();
        if (name == null || name.isBlank()) {
            Integer accountId = member.accountId();
            name = accountId == null
                    ? "Member_" + memberId
                    : accounts.findById(accountId)
                            .map(account -> NameParts.of(account).official())
                            .orElse("Member_" + memberId);
        }
        return name.replaceAll("[^a-zA-ZäöüÄÖÜß0-9 _-]", "");
    }

    /**
     * Every sheet of a run and its evaluation table, as one archive.
     *
     * @param run          the run, already checked to belong to the caller's station
     * @param protocolName the protocol the run is of
     */
    public byte[] archive(TestProtocolRun run, String protocolName) {
        try (var bytes = new ByteArrayOutputStream();
                var zip = new ZipOutputStream(bytes)) {
            add(zip, "Auswertung.pdf", pdfs.exportEvaluationTable(run.id(), protocolName, run.testDate()));
            for (var member : protocols.findRunMembers(run.id())) {
                add(
                        zip,
                        memberFileName(member.memberId()) + ".pdf",
                        pdfs.exportRunMember(run.id(), member.memberId(), protocolName, run.testDate()));
            }
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException | RuntimeException e) {
            log.error("Test protocol export failed", e);
            throw TestProtocolRefusal.PROTOCOL_RUN_NOT_EXPORTED.raise();
        }
    }

    private static void add(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    /**
     * A run as it is started or renamed. Renaming reads only the name and the date.
     *
     * @param testDate the day of the test, or {@code null} for today
     * @param memberIds the members to test, or {@code null} for none named directly
     * @param userTypes the kinds of member whose members are tested, or {@code null} for none
     * @param groupIds the groups whose members are tested, or {@code null} for none
     * @param tagIds the tags whose members are tested, or {@code null} for none
     */
    public record ProtocolRunRequest(
            String name,
            @Nullable LocalDate testDate,
            @Nullable List<Integer> memberIds,
            @Nullable List<String> userTypes,
            @Nullable List<Integer> groupIds,
            @Nullable List<Integer> tagIds) {}
}
