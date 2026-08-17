/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Locator, Page} from '@playwright/test'

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
 * "Mein Inventar" — and with the count an entry may carry allowed for, since a group showing how
 * many things wait in it reads as "Mitglieder 6" to anything asking for its name.
 *
 * Which element an entry is belongs to the sidebar's design rather than to what a story is about: a
 * group with a page of its own is a link, a group that only holds others is a button, and a subpoint
 * is always a link.
 */
export function sidebarEntry(page: Page, label: string): Locator {
    const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const name = new RegExp(`^${escaped}( \\d+)?$`)
    return sidebar(page).getByRole('link', {name}).or(sidebar(page).getByRole('button', {name}))
}
