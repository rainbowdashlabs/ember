/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.auth.StationUserType;

import java.util.List;

/**
 * A person on a witness's list of whom to collect signatures from, with what waits on them.
 *
 * @param memberId      the member
 * @param identity      the member with what their name is shown with: avatar, colour, tag
 * @param name          their official name, which the document and the signature carry
 * @param userType      what kind of member they are, which tells a trial user apart
 * @param groupIds      the groups they are in
 * @param openFields    how many fields of their documents wait to be signed in front of this witness
 * @param openDocuments the titles of the documents those fields are on, each once, oldest first
 */
public record WitnessCandidate(
        int memberId,
        MemberIdentity identity,
        String name,
        StationUserType userType,
        List<Integer> groupIds,
        int openFields,
        List<String> openDocuments) {}
