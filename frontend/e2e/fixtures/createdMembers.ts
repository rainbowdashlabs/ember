/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {request, type BrowserContext, type Page} from '@playwright/test'

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
    /** The session of whoever made them, since the removal runs after their page is closed. */
    cookies: Awaited<ReturnType<BrowserContext['cookies']>>
    /** Where the page that made them was, which is where the removal is sent. */
    origin: string
    /** The membership, which is what the removal names. Nothing rewrites it. */
    memberId: number
}

const made: Made[] = [];

/**
 * Notes somebody down to be removed when the story that made them ends.
 *
 * <p>The removal runs once the story's pages are closed, so the session of the page that made them
 * is noted down with them: the cookie is what signs the removal in, the headers carry the token a
 * change has to send back and the station.
 */
export async function remember(page: Page, headers: Record<string, string>, memberId: number) {
    made.push({headers, cookies: await page.context().cookies(), origin: new URL(page.url()).origin, memberId})
}

/**
 * Takes away everybody this worker's story made, each on a context carrying the session that made
 * them.
 *
 * <p>Failures are swallowed on purpose. A story that already removed its own person, or one whose
 * station is gone, has nothing left to clear, and a tidy-up that fails a passing test would be
 * worse than the untidiness it exists to prevent.
 *
 * <p>Each is removed by the id they were made with, since a story may have renamed them.
 */
export async function removeMade(): Promise<void> {
    const pending = made.splice(0)
    for (const entry of pending) {
        const context = await request.newContext({
            baseURL: entry.origin,
            storageState: {cookies: entry.cookies, origins: []},
        })
        try {
            await context.delete(`/api/v1/station-members/${entry.memberId}`, {headers: entry.headers})
                .catch(() => undefined)
        } finally {
            await context.dispose()
        }
    }
}
