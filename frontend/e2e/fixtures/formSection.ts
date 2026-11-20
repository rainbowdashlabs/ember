/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'

/**
 * Opens a folded section of the appointment, event group or template form, which is how a reader
 * reaches what is in it: its content stays in the page while it is closed, but hidden, so nothing in
 * it can be filled or clicked. A section that is open already is left open.
 *
 * @param page the page holding the form
 * @param key  the section, as its test id names it after `form-section-`
 */
export async function openFormSection(page: Page, key: string) {
    const toggle = page.getByTestId(`form-section-${key}`).first().locator('[data-section-toggle]').first()
    await toggle.waitFor()
    if (await toggle.getAttribute('aria-expanded') !== 'true') await toggle.click()
}
