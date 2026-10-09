/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.generator.entity.RequirementSignature;

/**
 * A document a partner station's appointment asks a member of this station to sign, filed in the member's
 * documents here and signed here.
 *
 * @param name       what the document is called
 * @param memberId   the member it was filed for
 * @param memberName the member's name
 * @param signature  where its signatures stand, with the fields the reader can sign now
 */
public record PartnerDocumentToSign(String name, int memberId, String memberName, RequirementSignature signature) {}
