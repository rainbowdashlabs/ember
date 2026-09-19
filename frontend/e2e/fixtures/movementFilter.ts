/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'

/** The movement queue's columns a story narrows by, by the heading they carry. */
export const MovementFilterColumn = {
    SUBJECT: 'Worum es geht',
    PURPOSE: 'Art',
    STEP: 'Schritt',
} as const

/**
 * Leaves one column filter of the movement queue ticking exactly the named entries, and applies it.
 *
 * <p>The filter is a list to tick rather than a single choice, so whatever it carried is taken off
 * first and the wanted entries are then ticked one after another. An empty list of entries therefore
 * leaves the column restricting nothing.
 *
 * <p>It lives here rather than beside one spec because the queue opens on what is still running: any
 * story about a movement that has ended has to take that filter off before its row is anywhere on the
 * page, whichever file the story sits in.
 *
 * @param column the heading of the column, one of {@link MovementFilterColumn}
 */
export async function setMovementFilter(page: Page, column: string, entries: string[]): Promise<void> {
    await page.getByRole('button', {name: `Filtern: ${column}`, exact: true}).click()
    const dialog = page.getByTestId('column-filter')
    await dialog.getByRole('button', {name: 'Keine', exact: true}).click()
    for (const entry of entries) await dialog.getByLabel(entry, {exact: true}).check()
    await dialog.getByTestId('column-filter-apply').click()
}
