/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {APIRequestContext} from '@playwright/test'

/**
 * The people a story made, and taking them away again when it is done.
 *
 * <p>Every story that needs somebody nobody else touches makes one, and until now none of them was
 * ever removed. The suite resets the database before the first story and not between them, so the
 * member list grew for the length of a run: a station seeded with forty-odd people ended a full run
 * holding well over a hundred. The screens that list people show a page at a time, so a story that
 * made somebody and then looked for them on a list found them near the top early in a run and off
 * the first page later on. That is why a failure moved from story to story between runs and why
 * every one of them passed when run alone.
 *
 * <p>Held in a module of its own so that the helper which makes people and the fixture which clears
 * them away need not import each other.
 */
interface Made {
    headers: Record<string, string>
    /** The membership, which is what the removal names. Nothing rewrites it. */
    memberId: number
}

const made: Made[] = [];

/** Notes somebody down to be removed when the story that made them ends. */
export function remember(headers: Record<string, string>, memberId: number) {
    made.push({headers, memberId})
}

/**
 * Takes away everybody this worker's story made.
 *
 * <p>Failures are swallowed on purpose. A story that already removed its own person, or one whose
 * station is gone, has nothing left to clear, and a tidy-up that fails a passing test would be
 * worse than the untidiness it exists to prevent.
 */
export async function removeMade(request: APIRequestContext): Promise<void> {
    const pending = made.splice(0)
    for (const entry of pending) {
        try {
            // By the id they were made with. Looking them up by surname again asked the name to be
            // both unique and unchanged, and a story that renames somebody is exactly what this
            // cleans up after.
            await request.delete(`/api/v1/station-members/${entry.memberId}`, {headers: entry.headers})
        } catch {
            /* a story that tore down its own station leaves nothing to clear */
        }
    }
}
