/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'

/**
 * Leaves one exchange filter ticking exactly the named entries, and closes it again.
 *
 * <p>The filter is a list to tick rather than a single choice, so whatever it carried is taken off
 * first and the wanted entries are then pressed one after another with the list left open between
 * them. An empty list of entries therefore leaves the filter restricting nothing. Closing it again
 * is what puts the rows underneath back within reach.
 *
 * <p>It lives here rather than beside one spec because the exchange list opens on the requests still
 * running: any story about an exchange that has ended has to take that tick off before its row is
 * anywhere on the page, whichever file the story sits in.
 *
 * <p>Each entry is pressed on the last button of that name, which is the one in the open list. The
 * button that opens the filter carries the name of a single ticked entry, so the first of the two is
 * the wrong one to press.
 */
export async function setExchangeFilter(page: Page, testId: string, entries: string[]): Promise<void> {
    const filter = page.getByTestId(testId)
    const trigger = filter.getByRole('button').first()
    await trigger.click()
    const none = filter.getByRole('button', {name: 'Keine'})
    if (await none.isEnabled()) await none.click()
    for (const entry of entries) await filter.getByRole('button', {name: entry, exact: true}).last().click()
    await trigger.click()
}
