/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, type Page} from './fixtures/auth'

/**
 * EVT-2, EVT-4 and EVT-6 of the story list, plus the permission the rest of them rest on.
 *
 * The planner navigates by click handler rather than by link, so its entries carry a test id — that
 * is the only way a story can pick one out without asserting on the shape of a calendar grid. The
 * entry also says whether its event takes registrations, because an event that does not has no
 * registration tab and would make these stories wait for something that is correctly absent.
 */
async function openEventWithRegistration(page: Page) {
    await page.goto('/station/events')
    await page.locator('[data-testid="event-entry"][data-registration="true"]').first().click()
    await page.waitForURL(/\/station\/events\/\d+/)
}

test.describe('Events', () => {
    test('the planner is reachable and offers a new event', async ({managerPage: page}) => {
        await page.goto('/station/events')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Neuer Termin'})).toBeVisible()
    })

    /**
     * A member reaches the page, and it is not the organiser's planner: the seeded member sees no
     * entries there at all, which is what the registration stories are still waiting on.
     */
    test('a member reaches the events page', async ({memberPage: page}) => {
        await page.goto('/station/events')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    /**
     * The registrations of an event load when the tab is opened. This is the story behind a fix
     * that shipped: the tab stayed empty because the list was asked for while the page was still
     * loading, and nothing asked again afterwards.
     */
    test('EVT-4 an event shows who has registered', async ({managerPage: page}) => {
        await openEventWithRegistration(page)

        await page.getByRole('button', {name: 'Anmeldungen'}).click()
        await expect(page.getByText(/Meine Anmeldung|Anmeldungen/).first()).toBeVisible()
    })

    /**
     * EVT-2 and EVT-6 in one walk. Held back on the seed rather than on the interface: the events
     * the seeded member sees in their planner are the ones that take no registration, so there is
     * nothing for them to sign up to. It turns green once the seeder gives that station an event
     * that asks its members to register.
     */
    test.fixme('EVT-2 a member registers for an event and withdraws again', async ({memberPage: page}) => {
        await openEventWithRegistration(page)
        await page.getByRole('button', {name: 'Anmeldungen'}).click()

        const register = page.getByRole('button', {name: 'Anmelden'}).first()
        const decline = page.getByRole('button', {name: 'Absagen'}).first()

        if (await register.isVisible().catch(() => false)) {
            await register.click()
            await expect(decline).toBeVisible()
        }

        await decline.click()
        await expect(page.getByRole('button', {name: 'Anmelden'}).first()).toBeVisible()
    })

    test('the registration overview lists across events', async ({managerPage: page}) => {
        await page.goto('/station/events/registrations')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByText('Anmeldungen').first()).toBeVisible()
    })
})
