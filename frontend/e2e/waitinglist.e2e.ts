/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders} from './fixtures/auth'

test.describe('Waiting lists', () => {
    test('the waiting lists of the station are reachable', async ({managerPage: page}) => {
        await page.goto('/station/members/waiting-lists')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        expect(page.url()).toContain('/station/members/waiting-lists')
    })

    test('a member does not reach the waiting lists', async ({memberPage: page}) => {
        await page.goto('/station/members/waiting-lists')

        await expect(page.getByRole('table')).toHaveCount(0)
    })

    /**
     * The public list is how somebody who is not in the station yet gets in touch. The story fills
     * it in as a stranger - no session at all - and is told what happens next, which is the address
     * being confirmed by mail.
     *
     * It takes the list that asks for nothing beyond a name and an address. The other two insist on
     * answers of their own, and a story guessing at what a station chose to ask would be testing the
     * seed rather than the registration.
     */
    test('a stranger registers on the public waiting list', async ({page}) => {
        const surname = `Interessent-${Date.now()}`

        await page.goto('/public/station/jugendfeuerwehr-musterstadt/waitlist')

        await page.getByText('Warteliste auswählen').waitFor()
        await page.getByText('Schnupperstunde').first().click()

        // The applicant's own three fields carry labels rather than placeholders - only the fields
        // for whoever looks after them are placeheld - so they are taken in the order they are asked.
        const fields = page.getByRole('textbox')
        await fields.nth(0).fill('Neu')
        await fields.nth(1).fill(surname)
        await fields.nth(2).fill(`${surname.toLowerCase()}@example.test`)

        // Somebody handing over their address has to be told what is done with it.
        const consent = page.getByRole('checkbox')
        if (await consent.count() > 0) await consent.first().check()

        await page.getByRole('button', {name: 'Anmeldung absenden'}).click()

        await expect(page.getByText(/Fast geschafft/)).toBeVisible()
    })

    /**
     * Not everybody arrives through the public form: somebody rings up and a manager writes them
     * down. The story does that on the same undemanding list and finds the entry on it afterwards.
     */
    test('an entry is added to a waiting list by hand', async ({managerPage: page}) => {
        const surname = `Anruf-${Date.now()}`

        await page.goto('/station/members/waiting-lists')
        await page.getByText('Schnupperstunde').first().click()
        await page.waitForURL(/\/station\/members\/waiting-lists\/(\d+)/)
        const id = page.url().match(/waiting-lists\/(\d+)/)?.[1]

        // Two blocks of the same fields: the person first, then whoever looks after them - required
        // when an entry is written down by hand, unlike on the public form, where whoever fills it
        // in is the one being asked.
        await page.goto(`/station/members/waiting-lists/${id}/entries/new`)
        await page.getByPlaceholder('Vorname').first().fill('Neu')
        await page.getByPlaceholder('Nachname').first().fill(surname)
        await page.getByPlaceholder('Vorname').nth(1).fill('Erika')
        await page.getByPlaceholder('Nachname').nth(1).fill('Muster')
        await page.getByPlaceholder('E-Mail-Adresse').first()
            .fill(`${surname.toLowerCase()}@example.test`)

        await page.getByRole('button', {name: 'Eintrag hinzufügen'}).click()

        await page.goto(`/station/members/waiting-lists/${id}`)
        await expect(page.getByText(surname).first()).toBeVisible()
    })

    /**
     * A list that knows where the date of birth is can work with ages. The field carries that by
     * its type, so an ordinary date field becomes the birth date without the answers moving.
     */
    test('a date field becomes the date of birth and the list sorts by it', async ({managerPage: page}) => {
        const fieldName = `Geburtstag-${Date.now()}`

        await page.goto('/station/members/waiting-lists')
        await page.getByText('Schnupperstunde').first().click()
        await page.waitForURL(/\/station\/members\/waiting-lists\/(\d+)/)
        const id = page.url().match(/waiting-lists\/(\d+)/)?.[1]

        await page.goto(`/station/members/waiting-lists/${id}/fields`)
        await page.getByRole('button', {name: 'Feld hinzufügen'}).click()
        await page.getByPlaceholder('Name des Feldes').fill(fieldName)
        await page.getByRole('combobox').first().selectOption('BIRTH_DATE')
        await page.getByRole('button', {name: 'Speichern'}).click()

        await page.reload()
        await expect(page.getByText('Geburtsdatum').first()).toBeVisible()

        // The list opens with a column of its own for it, sortable like the rest.
        await page.goto(`/station/members/waiting-lists/${id}`)
        await expect(page.getByRole('columnheader', {name: new RegExp(fieldName)})).toBeVisible()
        await page.getByRole('columnheader', {name: 'Vorname'}).click()
        await expect(page.getByRole('columnheader', {name: 'Vorname'})).toHaveAttribute('aria-sort', 'ascending')
    })

    /** One is what makes the age findable without being told where it is; two would be a guess. */
    test('a list takes only one date of birth field', async ({managerPage: page}) => {
        await page.goto('/station/members/waiting-lists')
        await page.getByText('Schnupperstunde').first().click()
        await page.waitForURL(/\/station\/members\/waiting-lists\/(\d+)/)
        const id = page.url().match(/waiting-lists\/(\d+)/)?.[1]

        await page.goto(`/station/members/waiting-lists/${id}/fields`)
        await page.getByRole('button', {name: 'Feld hinzufügen'}).click()
        await page.getByPlaceholder('Name des Feldes').fill(`Zweites-${Date.now()}`)
        await page.getByRole('combobox').first().selectOption('BIRTH_DATE')
        await page.getByRole('button', {name: 'Speichern'}).click()

        await expect(page.getByText(/already has a date of birth field|Fehler/)).toBeVisible()
    })

    /**
     * The whole of the first contact: a station invites somebody to one evening, they answer from
     * the link in the mail without signing in, and the answer is back on the entry the station is
     * looking at. Nothing is created along the way, and nobody is signed up for the appointment.
     *
     * The evening is picked out of what is coming up, so it is an appointment and one date of it.
     * The link the answer is given from is the entry's own and needs no session, which is the whole
     * point: somebody with no interest will not walk through an account just to say no.
     */
    test('an invitation names an evening and is answered from the entry link', async ({managerPage: page, page: visitor}) => {
        const surname = `Einladung-${Date.now()}`

        await page.goto('/station/members/waiting-lists')
        await page.getByText('Schnupperstunde').first().click()
        await page.waitForURL(/\/station\/members\/waiting-lists\/(\d+)/)
        const id = page.url().match(/waiting-lists\/(\d+)/)?.[1]

        await page.goto(`/station/members/waiting-lists/${id}/entries/new`)
        await page.getByPlaceholder('Vorname').first().fill('Neu')
        await page.getByPlaceholder('Nachname').first().fill(surname)
        await page.getByPlaceholder('Vorname').nth(1).fill('Erika')
        await page.getByPlaceholder('Nachname').nth(1).fill('Muster')
        await page.getByPlaceholder('E-Mail-Adresse').first()
            .fill(`${surname.toLowerCase()}@example.test`)
        await page.getByRole('button', {name: 'Eintrag hinzufügen'}).click()

        await page.goto(`/station/members/waiting-lists/${id}`)
        const row = page.getByRole('row').filter({hasText: surname})
        await row.getByRole('button', {name: 'Einladen'}).click()

        const occurrence = page.getByTestId('waitlist-invite-occurrence')
        await occurrence.locator('input[type="search"]').click()
        await occurrence.getByRole('button').first().click()
        await page.getByTestId('waitlist-invite-send').click()
        await expect(page.getByTestId('waitlist-invite-modal')).toHaveCount(0)

        const headers = await apiHeaders(page)
        const entries = await page.request.get(`/api/v1/waiting-lists/${id}/entries`, {headers})
        expect(entries.ok(), `the entries were readable (${entries.status()})`).toBeTruthy()
        const body = await entries.json()
        const invited = body.find((item: {entry: {lastname: string}}) => item.entry.lastname === surname)
        expect(invited, 'the entry that was just invited is on the list').toBeTruthy()

        await visitor.goto(`/waiting-list/status?token=${invited.entry.accessToken}`)
        await expect(visitor.getByTestId('waitlist-invitation'), 'the page says what it is about').toBeVisible()
        await visitor.getByTestId('waitlist-answer-coming').click()
        await expect(visitor.getByTestId('waitlist-answer-given')).toBeVisible()

        await page.reload()
        await expect(
            page.getByRole('row').filter({hasText: surname}).getByTestId('waitlist-answer-badge'),
            'the station finds the answer where it is already looking',
        ).toBeVisible()
    })

    /**
     * A field offering a choice is only worth having if the choices come back. They are saved as
     * an object and were read as though they were text, which left every such field looking empty
     * everywhere it was shown while the answers sat in the database intact.
     */
    test('the choices of a selection field survive being saved', async ({managerPage: page}) => {
        const fieldName = `Farbe-${Date.now()}`

        await page.goto('/station/members/waiting-lists')
        await page.getByText('Schnupperstunde').first().click()
        await page.waitForURL(/\/station\/members\/waiting-lists\/(\d+)/)
        const id = page.url().match(/waiting-lists\/(\d+)/)?.[1]

        await page.goto(`/station/members/waiting-lists/${id}/fields`)
        await page.getByRole('button', {name: 'Feld hinzufügen'}).click()
        await page.getByPlaceholder('Name des Feldes').fill(fieldName)
        await page.getByRole('combobox').first().selectOption('ENUM')
        await page.getByPlaceholder('Option 1, Option 2, Option 3').fill('rot, blau, grün')
        await page.getByRole('button', {name: 'Speichern'}).click()

        await page.reload()
        await expect(page.getByText('rot, blau, grün')).toBeVisible()

        await page.getByRole('button', {name: 'Bearbeiten'}).last().click()
        await expect(page.getByPlaceholder('Option 1, Option 2, Option 3')).toHaveValue('rot, blau, grün')
    })
})
