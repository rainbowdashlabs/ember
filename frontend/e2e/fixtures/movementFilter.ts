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
    await openColumnFilter(page, column)
    const dialog = page.getByTestId('column-filter')
    await dialog.getByRole('button', {name: 'Keine', exact: true}).click()
    for (const entry of entries) await dialog.getByLabel(entry, {exact: true}).check()
    await dialog.getByTestId('column-filter-apply').click()
}

/**
 * Opens one column's filter, from wherever the width at hand keeps it.
 *
 * <p>A table carries a filter button in every column heading. A phone has no headings: the rows are
 * cards, and the same filters hang in a menu above them. Both ways open the one dialog, so only the
 * way in differs, and a story that narrows a column reads the same in either project.
 *
 * <p>Which of the two is there has to be waited for rather than asked: the queue is still loading
 * when a story reaches this, and asking a heading that has not been drawn yet whether it is visible
 * answers no on a desktop as surely as on a phone.
 *
 * @param page   the page the queue is on
 * @param column the heading of the column, which the menu names as well
 */
async function openColumnFilter(page: Page, column: string): Promise<void> {
    const heading = page.getByRole('button', {name: `Filtern: ${column}`, exact: true})
    const menu = page.getByTestId('record-card-filters-trigger')
    await heading.or(menu).first().waitFor()
    if (await heading.isVisible()) {
        await heading.click()
        return
    }
    await menu.click()
    await page.getByTestId('record-card-filters').getByRole('button', {name: column, exact: true}).click()
}
