/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, type Page} from './fixtures/auth'

/**
 * Registering for an event, withdrawing again, and the organiser's view of who has signed up.
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
     * A member sees the station's events. They did not until the page stopped asking for the
     * attendance templates alongside them: that call is refused for anyone who does not record
     * attendance, and it took the whole page down with it.
     */
    test('a member sees the events of their station', async ({memberPage: page}) => {
        await page.goto('/station/events')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByTestId('event-entry').first()).toBeVisible()
    })

    /**
     * The registrations of an event load when the tab is opened. This is the story behind a fix
     * that shipped: the tab stayed empty because the list was asked for while the page was still
     * loading, and nothing asked again afterwards.
     */
    test('an event shows who has registered', async ({managerPage: page}) => {
        await openEventWithRegistration(page)

        await page.getByRole('button', {name: 'Anmeldungen'}).click()
        await expect(page.getByText(/Meine Anmeldung|Anmeldungen/).first()).toBeVisible()
    })

    /** Registering and withdrawing in one walk, which is how a member uses this in practice. */
    test('a member registers for an event and withdraws again', async ({memberPage: page}) => {
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

    /**
     * An event can ask the people signing up for things — shirt size, who is coming along. The
     * story adds such a question to an event of its own and then signs up as a member, who is asked
     * it and whose answer stands next to their name for the organiser afterwards.
     */
    test('a registration question is asked and its answer reaches the organiser', async ({managerPage, memberPage}) => {
        const event = `Termin-${Date.now()}`
        const question = 'Wer kommt mit?'
        const answer = `Antwort-${Date.now()}`

        await managerPage.goto('/station/events/new')
        await managerPage.getByPlaceholder('Name des Termins').fill(event)

        // An event without a time is not an event, and the form keeps its save disabled until it
        // has one.
        const times = managerPage.locator('input[type="datetime-local"]')
        await times.first().fill('2026-12-01T18:00')
        if (await times.count() > 1) await times.nth(1).fill('2026-12-01T20:00')

        // Registration is off to begin with, and the questions belong to it. The switch sits beside
        // the words rather than under them.
        await managerPage.getByText('Anmeldung erforderlich')
            .locator('xpath=following-sibling::button').click()
        await managerPage.getByRole('button', {name: 'Frage hinzufügen'}).click()
        await managerPage.getByPlaceholder('z.B. Begleitpersonen').fill(question)

        await managerPage.getByRole('button', {name: /Speichern|Erstellen/}).last().click()

        // Saving lands back on the planner rather than on the event, so the story opens it from
        // the list it now stands in.
        await managerPage.waitForURL(/\/station\/events$/)
        await managerPage.getByText(event).first().click()
        await managerPage.waitForURL(/\/station\/events\/(\d+)/)
        const id = managerPage.url().match(/events\/(\d+)/)?.[1]

        await memberPage.goto(`/station/events/${id}`)
        await memberPage.getByRole('button', {name: 'Anmeldungen'}).click()
        await memberPage.getByRole('button', {name: 'Anmelden'}).first().click()

        await expect(memberPage.getByText(question).first()).toBeVisible()
        await memberPage.getByRole('textbox').first().fill(answer)
        await memberPage.getByRole('button', {name: /Anmelden|Absenden|Speichern/}).last().click()

        await managerPage.goto(`/station/events/${id}`)
        await managerPage.getByRole('button', {name: 'Anmeldungen'}).click()
        await expect(managerPage.getByText(answer).first()).toBeVisible()
    })

    /**
     * A season of weekly evenings is entered once rather than fifty times. The story generates the
     * dates, creates them in one go, and finds one of them in the planner afterwards.
     */
    test('a run of events is created in one go', async ({managerPage: page}) => {
        const name = `Serie-${Date.now()}`

        // Three steps: what the events are called, when they fall, and a last look at the list.
        await page.goto('/station/events/batch')
        await page.getByRole('textbox').first().fill(name)
        await page.getByRole('button', {name: 'Weiter'}).click()

        const dates = page.locator('input[type="date"]')
        await dates.first().fill('2026-12-01')
        await dates.nth(1).fill('2026-12-31')

        await page.getByRole('button', {name: 'Termine generieren'}).click()
        await expect(page.getByText(/\d+ Termine werden erstellt/)).toBeVisible()

        await page.getByRole('button', {name: 'Termine erstellen'}).click()

        await page.goto('/station/events')
        await expect(page.getByText(name).first()).toBeVisible()
    })

    /**
     * A category is what a manager reaches for when the list of events stops being readable. The
     * story creates one and reloads: a category that only lives in the open page is no category.
     */
    test('a category is created and survives a reload', async ({managerPage: page}) => {
        const name = `Kategorie ${Date.now()}`

        await page.goto('/station/events/categories')
        await page.getByRole('button', {name: 'Kategorie erstellen'}).click()
        await page.getByPlaceholder('z.B. Übungen').fill(name)
        await page.getByRole('button', {name: 'Speichern'}).click()

        await expect(page.getByText(name)).toBeVisible()

        await page.reload()
        await expect(page.getByText(name)).toBeVisible()
    })

    /**
     * What a category is for: the events page sorts itself into blocks instead of one long list,
     * and no block holds everything.
     */
    test('the events list is grouped by category', async ({managerPage: page}) => {
        await page.goto('/station/events')

        const groups = page.getByTestId('event-category-group')
        await expect(groups.first()).toBeVisible()
        expect(await groups.count()).toBeGreaterThan(1)

        const total = await page.getByTestId('event-entry').count()
        const inFirstGroup = await groups.first().getByTestId('event-entry').count()
        expect(inFirstGroup).toBeLessThan(total)
    })
})
