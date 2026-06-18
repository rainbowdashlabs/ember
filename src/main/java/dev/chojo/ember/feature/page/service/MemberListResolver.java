/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.service;

import dev.chojo.ember.feature.media.service.ImageCategory;
import dev.chojo.ember.feature.media.service.ImageService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.page.entity.CellConfig.MemberListSortBy;
import dev.chojo.ember.feature.page.entity.CellConfig.ResolvedMember;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Package-private function bag for expanding an MEMBER_LIST_SPOTLIGHT (member-list) source descriptor
 * to a list of {@link ResolvedMember} render cards. Lives alongside its only callers
 * ({@code PageService} and {@code PageRoutes}) and intentionally **does not** declare any
 * lifecycle / DI annotations — callers pass their already-injected dependencies in. That keeps
 * the architecture honest: this is shared logic, not a separately-managed service that
 * shadows {@code StationMemberService} / {@code MemberIdentityFactory}.
 *
 * <p>The avatar inlining (as base64 {@code data:} URLs) is what allows public visitors to
 * render MEMBER_LIST_SPOTLIGHT cells without hitting the auth-gated {@code /members/.../avatar}
 * endpoint — i.e. the security model of the public pages stays intact.
 */
public final class MemberListResolver {

    private MemberListResolver() {}

    public static List<ResolvedMember> resolve(
            StationMemberRepository memberRepository,
            ImageService imageService,
            int stationId,
            JsonNode source,
            MemberListSortBy sortBy,
            Map<String, String> memberDescriptions,
            List<String> memberOrder) {
        if (source == null || !source.isObject()) return List.of();
        Map<String, String> descriptions = memberDescriptions != null ? memberDescriptions : Map.of();
        var picked =
                switch (source.path("kind").asString()) {
                    case "group" ->
                        source.path("groupId").isInt()
                                ? memberRepository.findOfficersByGroup(
                                        stationId, source.path("groupId").asInt())
                                : List.<StationMemberRepository.PickerMember>of();
                    case "tag" ->
                        source.path("tagId").isInt()
                                ? memberRepository.findOfficersByTag(
                                        stationId, source.path("tagId").asInt())
                                : List.<StationMemberRepository.PickerMember>of();
                    case "manual" -> resolveManual(memberRepository, stationId, source);
                    default -> List.<StationMemberRepository.PickerMember>of();
                };
        var sorted = sortMembers(picked, sortBy, memberOrder);
        return sorted.stream()
                .map(m -> toResolved(m, descriptions.get(m.memberUid().toString()), imageService))
                .toList();
    }

    private static List<StationMemberRepository.PickerMember> resolveManual(
            StationMemberRepository memberRepository, int stationId, JsonNode source) {
        var uidsNode = source.path("memberUids");
        if (!uidsNode.isArray()) return List.of();
        var uids = new ArrayList<UUID>(uidsNode.size());
        for (var n : uidsNode) {
            try {
                uids.add(UUID.fromString(n.asString()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        var byUid = new HashMap<UUID, StationMemberRepository.PickerMember>();
        for (var m : memberRepository.findOfficersByUids(stationId, uids)) {
            byUid.put(m.memberUid(), m);
        }
        var ordered = new ArrayList<StationMemberRepository.PickerMember>(uids.size());
        for (var uid : uids) {
            var match = byUid.get(uid);
            if (match != null) ordered.add(match);
        }
        return ordered;
    }

    private static List<StationMemberRepository.PickerMember> sortMembers(
            List<StationMemberRepository.PickerMember> members, MemberListSortBy sortBy, List<String> memberOrder) {
        if (members.isEmpty()) return members;
        var effective = sortBy != null ? sortBy : MemberListSortBy.ORDER;
        if (effective == MemberListSortBy.ORDER) {
            if (memberOrder == null || memberOrder.isEmpty()) return members;
            var rank = new HashMap<String, Integer>();
            for (int i = 0; i < memberOrder.size(); i++) rank.put(memberOrder.get(i), i);
            var copy = new ArrayList<>(members);
            copy.sort(
                    Comparator.comparingInt(m -> rank.getOrDefault(m.memberUid().toString(), Integer.MAX_VALUE)));
            return copy;
        }
        Comparator<StationMemberRepository.PickerMember> cmp =
                switch (effective) {
                    case NAME ->
                        Comparator.comparing(
                                StationMemberRepository.PickerMember::displayName,
                                Comparator.nullsLast(String::compareToIgnoreCase));
                    case ROLE ->
                        Comparator.comparing(
                                (StationMemberRepository.PickerMember m) -> m.userType() == null
                                        ? null
                                        : m.userType().name(),
                                Comparator.nullsLast(String::compareToIgnoreCase));
                    case JOIN_DATE ->
                        Comparator.comparing(
                                StationMemberRepository.PickerMember::joinDate,
                                Comparator.nullsLast(Comparator.naturalOrder()));
                    default -> null;
                };
        if (cmp == null) return members;
        var copy = new ArrayList<>(members);
        copy.sort(cmp);
        return copy;
    }

    private static ResolvedMember toResolved(
            StationMemberRepository.PickerMember m, String description, ImageService imageService) {
        return new ResolvedMember(
                m.memberUid().toString(),
                m.displayName(),
                m.userType() != null ? m.userType().name() : null,
                m.displayTag(),
                m.displayTagColor(),
                avatarDataUrl(imageService, m.memberUid()),
                description);
    }

    private static String avatarDataUrl(ImageService imageService, UUID memberUid) {
        return imageService
                .read(ImageCategory.AVATARS, memberUid.toString(), 64)
                .map(img -> "data:" + img.contentType() + ";base64,"
                        + Base64.getEncoder().encodeToString(img.data()))
                .orElse(null);
    }
}
