/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Where a witness has one field signed in front of them: the witness page, opened on that field.
 *
 * <p>An address rather than a named route, because the links that lead there are drawn by components a
 * page mounts without a router, and a pushed address reaches the same page.
 *
 * @param fieldId the open field to sign
 */
export function witnessSigningHref(fieldId: number): string {
    return `/station/signing/witness?field=${fieldId}`
}
