/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, type Ref} from 'vue'
import {browserRef} from '@/util/browserState'

/**
 * How many changes the reader's own requests have made so far, which the sidebar counters follow.
 *
 * <p>The request client raises it after every successful write, whatever the page, so no page has to
 * remember to read the counts again after changing something. It lives here rather than in a
 * composable because the api layer may not reach composables, the same way the toasts do. Only the
 * browser writes: a server render sends no writes, and on the server the value stays read-only.
 */
const changes = browserRef(0)

/** Records that a request changed something the counters may count. */
export function countsMayHaveChanged(): void {
    changes.value++
}

/** The changes made so far, for whoever keeps counters to watch. */
export function changesMade(): Readonly<Ref<number>> {
    return readonly(changes)
}
