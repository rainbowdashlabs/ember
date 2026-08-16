/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

/**
 * The reachability and permission halves of the event stories.
 *
 * The planner navigates by click handler rather than by link, so a story cannot pick an event out
 * of it by address. EVT-2 to EVT-6 — registering, answering the questions, the organiser's totals —
 * follow once the planner's entries carry a test id; guessing at a cell in a calendar grid would
 * make them tests of the layout instead of of the registration.
 */
test.describe('Events', () => {
    test('the planner is reachable and offers a new event', async ({managerPage: page}) => {
        await page.goto('/station/events')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Neuer Termin'})).toBeVisible()
    })

    /** Creating events belongs to whoever runs them; a member only takes part. */
    test('a member reaches the planner without being offered a new event', async ({memberPage: page}) => {
        await page.goto('/station/events')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Neuer Termin'})).toHaveCount(0)
    })

    test('the registration overview lists across events', async ({managerPage: page}) => {
        await page.goto('/station/events/registrations')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByText('Anmeldungen').first()).toBeVisible()
    })
})
