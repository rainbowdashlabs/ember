/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository.ClusterMemberRow;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The people at every station of a cluster, as its member managers search them.
 *
 * <p>Every list in Ember draws a person through their identity, which carries the avatar, the colour
 * and the display tag as well as the name. Assembling half of one in the browser would get the name
 * back and none of the rest, so each row carries the whole identity.
 */
@Singleton
public class ClusterMemberSearchService {
    private final ClusterMemberManagementService management;
    private final MemberGroupRepository groups;
    private final UserTagRepository tags;

    @Inject
    public ClusterMemberSearchService(
            ClusterMemberManagementService management, MemberGroupRepository groups, UserTagRepository tags) {
        this.management = management;
        this.groups = groups;
        this.tags = tags;
    }

    /**
     * One page of the search.
     *
     * @param clusterId the cluster whose stations are searched
     * @param search    what was asked for
     * @return the page, each row with its identity
     */
    public MemberPageResponse search(int clusterId, Search search) {
        var page = management.search(
                clusterId,
                search.query(),
                search.stationId(),
                search.userType(),
                search.includeFormer(),
                search.page(),
                search.size());
        var ids = page.members().stream().map(ClusterMemberRow::id).toList();
        var colors = groups.findNameColors(ids);
        var displayTags = tags.findDisplayTags(ids);
        return new MemberPageResponse(
                page.members().stream()
                        .map(row -> toResponse(row, colors.get(row.id()), displayTags.get(row.id())))
                        .toList(),
                page.total(),
                page.page(),
                page.size());
    }

    private static ManagedMemberResponse toResponse(ClusterMemberRow row, String nameColor, UserTag tag) {
        return new ManagedMemberResponse(
                row.id(),
                row.uid(),
                row.stationUid(),
                row.stationName(),
                row.name(),
                row.email(),
                row.userType().name(),
                row.joinDate(),
                row.former(),
                row.stationOwner(),
                new MemberIdentity(
                        row.stationUid(),
                        row.uid(),
                        row.name(),
                        row.stationName(),
                        nameColor,
                        tag == null ? null : new MemberIdentity.DisplayTag(tag.name(), tag.color())),
                row.stationNames());
    }

    /**
     * What a member manager searched for.
     *
     * @param query         a fragment of the name or address, or null
     * @param stationId     narrow to one station, or null
     * @param userType      narrow to one kind of member, or null
     * @param includeFormer whether people who have left are listed too
     * @param page          the page, from zero
     * @param size          the page size
     */
    public record Search(
            @Nullable String query,
            @Nullable Integer stationId,
            @Nullable StationUserType userType,
            boolean includeFormer,
            int page,
            int size) {}

    /**
     * @param stationOwner whether they are their station's owner, which the cluster may not edit
     * @param stationNames every station of this association the person belongs to, so a row can say so
     *                     rather than naming only the membership it came from
     */
    public record ManagedMemberResponse(
            int id,
            UUID uid,
            UUID stationUid,
            String stationName,
            String name,
            @Nullable String email,
            String userType,
            LocalDate joinDate,
            boolean former,
            boolean stationOwner,
            MemberIdentity identity,
            String stationNames) {}

    /**
     * @param total how many the search found altogether, not how many are on this page
     */
    public record MemberPageResponse(List<ManagedMemberResponse> members, int total, int page, int size) {}
}
