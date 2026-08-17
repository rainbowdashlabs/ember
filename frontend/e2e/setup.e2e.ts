/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect, demoAccounts, pageAsThrowaway} from './fixtures/auth'

/**
 * The assistant a fresh station is led through.
 *
 * Two stations are involved. The seeded main station is past its setup, and a station that is past it
 * is sent away from the assistant rather than shown it again. The seeder also leaves one station
 * deliberately unfinished, with an administrator of its own, and that is the station these stories
 * walk - the only way to see the assistant at all.
 *
 * They run one after another, and the story that finishes the assistant runs last: finishing it is
 * exactly what takes the assistant away from the others.
 */
test.describe.configure({mode: 'serial'})

/** The station the seeder leaves unfinished, addressed by the identifier the seeder fixes for it. */
const SETUP_STATION = '00000000-0000-4000-a000-000000000002'

async function setupStationPage(browser: Parameters<typeof pageAsThrowaway>[0], request: Parameters<typeof pageAsThrowaway>[1]): Promise<Page> {
    const accounts = await demoAccounts(request)
    const manager = accounts.find(account =>
        account.stationId === SETUP_STATION && account.permissions.includes('STATION_ADMINISTRATOR'))
    if (!manager) throw new Error('The seeder no longer leaves a station in its setup')
    return pageAsThrowaway(browser, request, [], manager)
}

test.describe('Setup assistant', () => {
    test('a station past its setup is not led through the assistant again', async ({managerPage: page}) => {
        await page.goto('/station/setup')

        await expect(page).toHaveURL(/\/station\/dashboard\/overview/)
    })

    test('the steps of the assistant send a finished station away too', async ({managerPage: page}) => {
        for (const step of ['/station/setup/address', '/station/setup/modules', '/station/setup/groups']) {
            await page.goto(step)
            await expect(page).toHaveURL(/\/station\/dashboard\/overview/)
        }
    })

    test('a member is led through no assistant', async ({memberPage: page}) => {
        await page.goto('/station/setup')

        await expect(page.getByRole('button', {name: /Weiter|Speichern/})).toHaveCount(0)
    })

    /** A station still being set up stays in the assistant, and it says which step of how many. */
    test('an unfinished station opens on the assistant', async ({browser, request}) => {
        const page = await setupStationPage(browser, request)

        await page.goto('/station/setup')

        expect(page.url()).toContain('/station/setup')
        await expect(page.getByText(/Schritt \d+ von \d+/)).toBeVisible()

        await page.context().close()
    })

    /**
     * What the assistant writes is written for good. The story makes a group and comes back to the
     * step: a group that only lived in the open page would be gone.
     *
     * Read inside the assistant rather than in member management, because a station still being set
     * up is sent back into the assistant from everywhere else - which is the point of it.
     */
    test('a group made in the assistant is kept', async ({browser, request}) => {
        const page = await setupStationPage(browser, request)
        const group = `Gruppe-${Date.now()}`

        // The name is typed into a line of its own and added to the list; the step then saves the
        // list it holds.
        await page.goto('/station/setup/groups')
        await page.getByPlaceholder('Name der Gruppe').first().fill(group)
        await page.getByRole('button', {name: 'Zeile hinzufügen'}).click()
        await expect(page.getByText(group).first()).toBeVisible()

        await page.getByRole('button', {name: 'Speichern und weiter'}).click()

        await page.goto('/station/setup/groups')
        await expect(page.getByText(group).first()).toBeVisible()

        await page.context().close()
    })

    /** The first event is not made in the assistant but in the planner it hands over to. */
    test('the assistant hands over to the event editor', async ({browser, request}) => {
        const page = await setupStationPage(browser, request)

        await page.goto('/station/setup/first-event')
        await page.getByRole('button', {name: /Termin|Event/}).first().click()

        await expect(page).toHaveURL(/\/station\/events\/new/)

        await page.context().close()
    })

    /**
     * The whole assistant, from the welcome to the end. Last of these stories on purpose: a station
     * that reaches the end is past its setup, and the assistant is then closed to it.
     */
    test('the assistant is walked to the end and the station is set up', async ({browser, request}) => {
        const page = await setupStationPage(browser, request)

        // The assistant's own address forwards to whichever step is open, so the walk starts once
        // that has happened rather than on the forwarding page itself.
        await page.goto('/station/setup')
        await page.waitForURL(/\/station\/setup\/\w/)

        // Every step either gets started, saves and moves on, or is skipped. What a step insists on
        // is answered; the rest is left as it is, which is what a station in a hurry would do.
        // Twelve steps, and a couple of them take two presses - the bound is only there so a step
        // that refuses to move on ends the story rather than the run.
        for (let step = 0; step < 30; step += 1) {
            if (page.url().includes('/station/setup/finish')) break

            // The address step is the one that insists: every field of it, plus a pin on the map,
            // which is what the two coordinate fields are.
            const address: [RegExp, string][] = [
                [/Hauptstraße 1/, 'Musterweg 1'],
                [/80331/, '80331'],
                [/München/, 'Neuhausen'],
                [/48\.137154/, '48.137154'],
                [/11\.576124/, '11.576124'],
            ]
            for (const [placeholder, value] of address) {
                const field = page.getByPlaceholder(placeholder)
                if (await field.count() > 0) await field.first().fill(value)
            }
            const country = page.locator('select:has(option:text-is("– bitte wählen –"))')
            if (await country.count() > 0) await country.first().selectOption({index: 1})

            // The location has a save of its own, and the step reads what was saved rather than
            // what stands in the fields - so it is pressed before moving on.
            const sectionSave = page.getByRole('button', {name: 'Speichern', exact: true})
            if (await sectionSave.count() > 0) await sectionSave.first().click()

            const before = page.url()
            const start = page.getByRole('button', {name: 'Loslegen'})
            const save = page.getByRole('button', {name: 'Speichern und weiter'})
            // Most steps offer skipping as a button; the one that hands over to another page offers
            // it as a link.
            const skip = page.getByRole('button', {name: 'Überspringen'})
                .or(page.getByRole('link', {name: 'Überspringen'}))

            // An optional step keeps its save switched off until something is written into it, and
            // is left behind rather than filled in.
            const canSave = await save.count() > 0 && await save.first().isEnabled()

            if (await start.count() > 0) await start.first().click()
            else if (canSave) await save.first().click()
            else if (await skip.count() > 0) await skip.first().click()
            else break

            await expect(async () => {
                expect(page.url()).not.toBe(before)
            }).toPass({timeout: 10_000})
        }

        await expect(page).toHaveURL(/\/station\/setup\/finish/)
        await expect(page.getByText('Geschafft!')).toBeVisible()

        await page.getByRole('button', {name: 'Zum Dashboard'}).click()
        await expect(page).toHaveURL(/\/station\/dashboard\/overview/)

        // And from now on the assistant is closed to this station like to any other.
        await page.goto('/station/setup')
        await expect(page).toHaveURL(/\/station\/dashboard\/overview/)

        await page.context().close()
    })
})
