/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders, type Page} from './fixtures/auth'

/**
 * A list of this story's own, for a story that changes the fields of one.
 *
 * <p>A list takes one date of birth and no more, so two stories adding one to the seeded
 * Schnupperstunde at the same time settle it between them: whichever arrives second is refused, and
 * the column the first went looking for is whatever the other left behind.
 *
 * @param page a page signed in as somebody who may keep the waiting lists
 * @return the id of the new list
 */
async function ownList(page: Page): Promise<string> {
    const created = await page.request.post('/api/v1/waiting-lists', {
        headers: await apiHeaders(page),
        data: {name: `Eigene Liste ${Date.now()}-${Math.floor(Math.random() * 10000)}`},
    })
    expect(created.ok(), `the story makes a list of its own (${await created.text()})`).toBeTruthy()
    return String((await created.json()).id)
}

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
        const id = await ownList(page)

        await page.goto(`/station/members/waiting-lists/${id}/fields`)
        await page.getByRole('button', {name: 'Feld hinzufügen'}).click()
        await page.getByPlaceholder('Name des Feldes').fill(fieldName)
        await page.getByRole('combobox').first().selectOption('BIRTH_DATE')
        await page.getByRole('button', {name: 'Speichern'}).click()

        await page.reload()
        await expect(page.getByText('Geburtsdatum').first()).toBeVisible()

        // Somebody has to be on it, or the list draws the words for an empty one and no columns at
        // all, which is a table with no headers rather than a table missing one.
        const entered = await page.request.post(`/api/v1/waiting-lists/${id}/entries`, {
            headers: await apiHeaders(page),
            data: {firstname: 'Testperson', lastname: `Wartend-${Date.now()}`},
        })
        expect(entered.ok(), `somebody stands on the list (${await entered.text()})`).toBeTruthy()

        // The list opens with a column of its own for it, sortable like the rest.
        await page.goto(`/station/members/waiting-lists/${id}`)
        await expect(page.getByRole('columnheader', {name: new RegExp(fieldName)})).toBeVisible()
        await page.getByRole('columnheader', {name: 'Vorname'}).getByTestId('column-sort').click()
        await expect(page.getByRole('columnheader', {name: 'Vorname'})).toHaveAttribute('aria-sort', 'ascending')
    })

    /** One is what makes the age findable without being told where it is; two would be a guess. */
    test('a list takes only one date of birth field', async ({managerPage: page}) => {
        const id = await ownList(page)

        // The first one is put there by this story rather than taken from the seed, so that what the
        // second one runs into is a field this story knows about and nobody else can take away.
        const first = await page.request.post(`/api/v1/waiting-lists/${id}/fields`, {
            headers: await apiHeaders(page),
            data: {name: `Erstes-${Date.now()}`, fieldType: 'BIRTH_DATE', position: 0, required: false},
        })
        expect(first.ok(), `the list has one to begin with (${await first.text()})`).toBeTruthy()

        await page.goto(`/station/members/waiting-lists/${id}/fields`)
        await page.getByRole('button', {name: 'Feld hinzufügen'}).click()
        await page.getByPlaceholder('Name des Feldes').fill(`Zweites-${Date.now()}`)
        await page.getByRole('combobox').first().selectOption('BIRTH_DATE')
        await page.getByRole('button', {name: 'Speichern'}).click()

        await expect(page.getByText(/already has a date of birth field|Fehler/)).toBeVisible()
    })

    /**
     * The whole of the first contact: a station invites somebody to one occurrence, they answer from
     * the link in the mail without signing in, and the answer is back on the entry the station is
     * looking at. Nothing is created along the way, and nobody is signed up for the appointment.
     *
     * The occurrence is picked out of what is coming up, so it is an appointment and one date of it.
     * The link the answer is given from is the entry's own and needs no session, which is the whole
     * point: somebody with no interest will not walk through an account just to say no.
     */
    test('an invitation names an occurrence and is answered from the entry link', async ({managerPage: page, page: visitor}) => {
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
     * A station nobody outside its own network can reach sends links nobody can open, so its lists
     * write to nobody. The story makes such a list, opens it to the public, and registers on it as
     * a stranger who leaves no address at all: the registration is on the list the moment it is
     * sent, rather than waiting for a confirmation that could never arrive.
     */
    test('a list that sends no mail takes a registration without confirming it', async ({managerPage: page, page: visitor}) => {
        const listName = `Ohne Mail ${Date.now()}`
        const surname = `Ohnemail-${Date.now()}`

        await page.goto('/station/members/waiting-lists')
        await page.getByRole('button', {name: 'Erstellen'}).click()

        const dialog = page.getByTestId('modal')
        await dialog.getByPlaceholder('z.B. Warteliste 2026').fill(listName)
        await dialog.getByTestId('waitlist-mail-toggle').getByRole('switch').click()
        await dialog.getByRole('button', {name: 'Erstellen'}).click()

        await page.waitForURL(/\/station\/members\/waiting-lists\/(\d+)/)
        const id = page.url().match(/waiting-lists\/(\d+)/)?.[1]

        await page.getByRole('button', {name: 'Bearbeiten', exact: true}).first().click()
        await expect(
            page.getByTestId('waitlist-mail-toggle').getByRole('switch'),
            'the list was made with its mails switched off',
        ).toHaveAttribute('aria-checked', 'false')
        await page.getByTestId('waitlist-public-toggle').getByRole('switch').click()
        await page.getByRole('button', {name: 'Speichern'}).click()

        const headers = await apiHeaders(page)
        const station = await page.request.get('/api/v1/station/manage', {headers})
        expect(station.ok(), `the station was readable (${station.status()})`).toBeTruthy()
        const slug = (await station.json()).publicSlug

        await visitor.goto(`/public/station/${slug}/waitlist`)
        await visitor.getByText(listName).first().click()

        const fields = visitor.getByRole('textbox')
        await fields.nth(0).fill('Kein')
        await fields.nth(1).fill(surname)
        const consent = visitor.getByRole('checkbox')
        if (await consent.count() > 0) await consent.first().check()

        await visitor.getByRole('button', {name: 'Anmeldung absenden'}).click()

        await expect(
            visitor.getByText('Anmeldung eingegangen'),
            'nobody is sent to an inbox for a link that was never written',
        ).toBeVisible()

        const entries = await page.request.get(`/api/v1/waiting-lists/${id}/entries`, {headers})
        expect(entries.ok(), `the entries were readable (${entries.status()})`).toBeTruthy()
        const body = await entries.json()
        const landed = body.find((item: {entry: {lastname: string; status: string}}) => item.entry.lastname === surname)
        expect(landed, 'the registration is on the list without anything being confirmed').toBeTruthy()
        expect(landed.entry.status, 'and waits for the station to look at it').toBe('PENDING')
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

        await page.getByTestId('question-option-add').click()
        await page.getByTestId('question-option-0').fill('rot')
        await page.getByTestId('question-option-add').click()
        await page.getByTestId('question-option-1').fill('blau, dunkel')
        await page.getByRole('button', {name: 'Speichern'}).click()

        await page.reload()
        await page.getByRole('button', {name: 'Bearbeiten'}).last().click()

        await expect(page.getByTestId('question-option-0')).toHaveValue('rot')
        await expect(
            page.getByTestId('question-option-1'),
            'a choice with a comma in it is one choice and not two',
        ).toHaveValue('blau, dunkel')
        await expect(page.getByTestId('question-option-2')).toHaveCount(0)
    })
})
