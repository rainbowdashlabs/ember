/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {RouteLocationRaw, Router} from 'vue-router'

/**
 * Returns to the page the reader came from, or opens the fallback where this page was the first in
 * the tab, such as one opened from a mail or a bookmark.
 *
 * @param router   the app's router, whose history entries remember the page before
 * @param fallback where to go when there is no page before
 */
export function goBack(router: Router, fallback: RouteLocationRaw): void {
    if (window.history.state?.back) {
        router.back()
    } else {
        router.push(fallback)
    }
}
