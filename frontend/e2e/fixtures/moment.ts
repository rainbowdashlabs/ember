/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Locator, Page} from '@playwright/test'

/** Every field that asks for a day and a time of day, in the order the page reads. */
export function momentFields(page: Page): Locator {
    return page.locator('[data-moment]')
}

/**
 * Enters a moment, written as `yyyy-MM-ddTHH:mm`, into a field that asks for the day and the time of
 * day separately.
 */
export async function fillMoment(field: Locator, moment: string): Promise<void> {
    await field.locator('input[type="date"]').fill(moment.slice(0, 10))
    await field.locator('input[type="time"]').fill(moment.slice(11, 16))
}
