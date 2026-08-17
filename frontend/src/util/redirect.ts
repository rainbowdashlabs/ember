/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Whether a redirect target carried in a query parameter may be followed.
 *
 * A usable target is a path on this instance - never another host, and never one of the two pages
 * that exist to send a visitor onwards. A target pointing back at the station picker or at the
 * requirements interstitial loops: the page redirects to the target, the target redirects back.
 *
 * @param target the raw value of the redirect parameter
 * @return whether it can be navigated to
 */
export function usableRedirect(target: string | null | undefined): target is string {
    if (!target) return false
    if (!target.startsWith('/') || target.startsWith('//')) return false
    return !target.startsWith('/cross-station') && !target.startsWith('/station/requirements')
}
