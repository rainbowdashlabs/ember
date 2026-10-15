/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, type Locator, type Page} from '@playwright/test'

/**
 * The sidebar of the station shell, which is the whole navigation: what a station offers is what
 * stands in here, so the stories about modules and permissions read it rather than trying page
 * addresses one by one.
 */
export function sidebar(page: Page): Locator {
    return page.getByRole('complementary')
}

/**
 * One entry of the sidebar, named by the words on it.
 *
 * Matched to the whole entry and not to a part of it, because "Inventar" would otherwise also find
 * "Mein Inventar" - and with the count an entry may carry allowed for, since a section showing how
 * many things wait in it reads as "Mitglieder 6" to anything asking for its name.
 *
 * The sidebar shows one section at a time: the full list holds pages (links) and sections (buttons),
 * an open section holds its pages and its subgroups (buttons). An entry is found only where it shows,
 * so a story reading the full list goes there first with {@link allSections}.
 */
export function sidebarEntry(page: Page, label: string): Locator {
    const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const name = new RegExp(`^${escaped}( \\d+)?$`)
    return sidebar(page).getByRole('link', {name}).or(sidebar(page).getByRole('button', {name}))
}

/**
 * Leaves the open section for the full list of the sidebar. Arriving on a page opens its section, so
 * a story asking about the sections themselves or about top-level pages starts here.
 */
export async function allSections(page: Page): Promise<void> {
    const back = sidebar(page).getByRole('button', {name: 'Alle Bereiche'})
    await sidebar(page).locator('[data-back], [data-section], a').first().waitFor()
    await expect(async () => {
        if (await back.isVisible()) await back.click()
        await expect(back).toHaveCount(0, {timeout: 2_000})
    }).toPass({timeout: 15_000})
}

/** Opens one section from the full list, which also opens its first page. */
export async function openSection(page: Page, label: string): Promise<void> {
    await allSections(page)
    await sidebarEntry(page, label).click()
    await expect(sidebar(page).getByRole('button', {name: 'Alle Bereiche'})).toBeVisible()
}

/**
 * The sidebar entry marked as the one you are standing in: the open section's heading, or in the full
 * list the section or page holding the page shown.
 *
 * Read off the markup rather than off a colour: which element is lit is the sidebar's design, but
 * that exactly one is and that it is the right one is a rule, and a rule is what a story asserts.
 */
export function activeSidebarGroup(page: Page): Locator {
    return sidebar(page).locator('[data-active="true"]')
}
