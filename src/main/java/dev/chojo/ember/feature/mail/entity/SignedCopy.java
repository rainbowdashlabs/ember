/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

import org.jspecify.annotations.Nullable;

/**
 * The copy of a signed document a signer gets once the version carrying their signature is sealed.
 *
 * <p>The SHA-256 of the sealed file is what makes it a copy: the file cannot print its own hash, so the
 * mail the signer received is where it is kept outside the installation. The PDF itself goes along only
 * where the document's template allows it, and only up to a size a mailbox takes.
 *
 * @param stationName   the station that sealed it
 * @param documentTitle the document's title
 * @param memberName    the official name of the member the document is about
 * @param signerName    the official name of whoever signed
 * @param signedAt      when they signed, written in the station's language and zone
 * @param sealedSha256  SHA-256 of the sealed version that first carries the signature, lower-case hexadecimal
 * @param documentUrl   where the document is opened in Ember
 * @param verifyUrl     where any copy of it can be checked
 * @param attachment    the sealed PDF, or null where it does not go along
 * @param tooLarge      whether it was left out for its size rather than because its template keeps it out
 */
public record SignedCopy(
        String stationName,
        String documentTitle,
        String memberName,
        String signerName,
        String signedAt,
        String sealedSha256,
        String documentUrl,
        String verifyUrl,
        @Nullable MailAttachment attachment,
        boolean tooLarge) {}
