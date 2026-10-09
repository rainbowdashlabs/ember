/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * Whether a document a federation partner sent may be taken as sealed by that partner. Where a document
 * carries several seals, the worst of them decides, in the order below from {@link #ALTERED} down to
 * {@link #UNDECIDED}; only when every seal is accepted is the document.
 */
public enum PartnerSealVerdict {
    /** Every seal is the partner station's, unchanged and valid. */
    ACCEPTED,
    /** The file is no PDF that can be checked, or larger than a check takes. */
    UNREADABLE,
    /** The document carries no seal at all. */
    NO_SEAL,
    /** A seal no longer matches the document, or the document was changed after it was sealed. */
    ALTERED,
    /**
     * A seal was not made with a key of the partner station under an authority pinned for the
     * partnership: another station's, a stranger's, or one under an authority the partner never stated.
     */
    NOT_FROM_PARTNER,
    /** The partner's key was revoked and nothing proves the seal was made before. */
    REVOKED,
    /** A seal of the partner failed for another reason, such as its certificate. */
    NOT_VALID,
    /** A seal of the partner could not be settled now, such as without current revocation data; worth asking again later. */
    UNDECIDED
}
