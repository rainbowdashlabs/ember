/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.members.service.GuardianPolicy;

/**
 * The signing services of one station wired against the database, as {@code GeneratorTestBase.signing}
 * builds them, so a test that asks for signatures does not spell out every collaborator.
 *
 * @param guardians  who looks after whom
 * @param guards     who may change and read the documents
 * @param notices    the real notices, writing notifications and queueing mail
 * @param requests   the signature requests
 * @param procedures the documents steps of procedures ask to sign
 */
public record SigningWiring(
        GuardianPolicy guardians,
        SigningGuards guards,
        SignatureNotices notices,
        SignatureRequestService requests,
        ProcedureSignatures procedures) {}
