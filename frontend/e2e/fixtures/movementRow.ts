/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Locator, Page} from '@playwright/test'

/**
 * The row of the movement queue that stands for one movement, whether it is drawn as a table row or
 * as a card. The movement's id sits on what the row is about, so the row is the one holding it.
 */
export function movementRow(page: Page, id: number): Locator {
    return page.getByTestId('movement-row').filter({has: page.locator(`[data-movement="${id}"]`)})
}
