/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository.PickerMember;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * The members a picker offers: active members of one station, found by name or by their UUID.
 */
@Singleton
public class MemberPickerService {
    private static final int MOST = 20;

    private final StationMemberRepository memberRepository;
    private final AvatarService avatarService;

    @Inject
    public MemberPickerService(StationMemberRepository memberRepository, AvatarService avatarService) {
        this.memberRepository = memberRepository;
        this.avatarService = avatarService;
    }

    /**
     * Finds members of a station for a picker.
     *
     * <p>A UUID wins over a search text: a picker that already holds somebody asks for exactly
     * them. One that is not a UUID finds nobody rather than refusing, since it only ever comes from
     * a picker's own state.
     *
     * @param stationId the station
     * @param search    a fragment of the name, or empty for the most recently joined
     * @param uid       one member's UUID as the client sent it, or null
     * @param limit     how many at most, held between 1 and 20
     * @return the members found
     */
    public List<MemberSearchResult> search(int stationId, String search, String uid, int limit) {
        if (uid != null && !uid.isBlank()) {
            UUID lookup;
            try {
                lookup = UUID.fromString(uid);
            } catch (IllegalArgumentException e) {
                return List.of();
            }
            return memberRepository
                    .findPickerByUid(stationId, lookup)
                    .map(this::toSearchResult)
                    .map(List::of)
                    .orElseGet(List::of);
        }
        return memberRepository.searchForPicker(stationId, search, Math.clamp(limit, 1, MOST)).stream()
                .map(this::toSearchResult)
                .toList();
    }

    private MemberSearchResult toSearchResult(PickerMember m) {
        return new MemberSearchResult(
                m.memberUid(),
                m.displayName(),
                m.userType() != null ? m.userType().name() : null,
                m.nameColor(),
                m.displayTag(),
                m.displayTagColor(),
                avatarDataUrlFor(m.accountUid()));
    }

    /**
     * Inlines the member's avatar as a {@code data:} URL so it can be rendered without
     * re-authenticating against the protected avatar endpoint. Answers {@code null} when the
     * member's account has no avatar or the member is no longer linked to an account.
     */
    private String avatarDataUrlFor(UUID accountUid) {
        if (accountUid == null) return null;
        return avatarService
                .read(accountUid, 64)
                .map(img -> "data:" + img.contentType() + ";base64,"
                        + Base64.getEncoder().encodeToString(img.data()))
                .orElse(null);
    }

    /**
     * Picker result shape: the avatar is inlined as a {@code data:} URL so the frontend can render
     * it without a separate authenticated request. {@code displayTag} carries the member's
     * highest-priority visible tag (and color); {@code null} when none is set.
     */
    public record MemberSearchResult(
            UUID memberUid,
            String displayName,
            @Nullable String userType,
            @Nullable String nameColor,
            @Nullable String displayTag,
            @Nullable String displayTagColor,
            @Nullable String avatarUrl) {}
}
