/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

/**
 * Configuring who carries a station's post.
 *
 * The providers are one list worked from the top, so the stories are about the list: adding an
 * entry, giving it an allowance, putting a second one behind it and moving it in front. There is
 * no separate first provider to set up before the list makes sense, and that is the point being
 * held in place here.
 *
 * They run one after another because they all write the same list, and a second story saving while
 * the first is halfway through would overwrite what it just wrote.
 */
test.describe.configure({mode: 'serial'})

const MAILING = '/station/manage/mailing'

/** The rows of the list, in the order they are tried. */
function rows(page: import('@playwright/test').Page) {
    return page.getByText(/^\d+\. Anbieter$/)
}

async function save(page: import('@playwright/test').Page) {
    await page.getByTestId('mail-providers-save').click()
    await expect(page.getByText('Anbieter gespeichert.')).toBeVisible()
}

test.describe('Station mail providers', () => {
    test('a station starts with no provider and says so', async ({managerPage: page}) => {
        await page.goto(MAILING)

        await expect(page.getByText(/Noch kein Anbieter eingetragen/)).toBeVisible()
    })

    test('a provider is added, given an allowance and kept', async ({managerPage: page}) => {
        await page.goto(MAILING)

        await page.getByRole('button', {name: 'Anbieter hinzufügen'}).click()
        await expect(rows(page)).toHaveCount(1)

        await page.getByLabel('Anbieter', {exact: true}).selectOption('SMTP')
        await page.getByLabel('Server', {exact: true}).fill('smtp.example.org')
        await page.getByLabel('Absender-Adresse').fill('post@example.org')
        await page.getByLabel('Absender-Name').fill('Wache')
        await page.getByLabel('Tageslimit').fill('300')
        await save(page)

        await page.reload()
        await expect(page.getByLabel('Server', {exact: true})).toHaveValue('smtp.example.org')
        await expect(page.getByLabel('Tageslimit')).toHaveValue('300')
    })

    test('a second provider goes behind the first and can be moved in front', async ({managerPage: page}) => {
        await page.goto(MAILING)
        await expect(rows(page)).toHaveCount(1)

        await page.getByRole('button', {name: 'Anbieter hinzufügen'}).click()
        await expect(rows(page)).toHaveCount(2)
        await page.getByLabel('Anbieter', {exact: true}).nth(1).selectOption('BREVO')
        await page.getByLabel('Absender-Adresse').nth(1).fill('zweit@example.org')
        await save(page)

        await page.reload()
        await expect(page.getByLabel('Anbieter', {exact: true}).first()).toHaveValue('SMTP')
        await expect(page.getByLabel('Anbieter', {exact: true}).nth(1)).toHaveValue('BREVO')

        await page.getByRole('button', {name: 'Nach oben'}).nth(1).click()
        await save(page)

        await page.reload()
        await expect(page.getByLabel('Anbieter', {exact: true}).first()).toHaveValue('BREVO')
    })

    /**
     * Every entry carries an address of its own, because the address ends in the report format the
     * provider sends. The one further down is exactly the one nobody would think to ask for until
     * it is carrying the post.
     */
    test('every provider is offered its own address for delivery reports', async ({managerPage: page}) => {
        await page.goto(MAILING)

        const addresses = page.getByTestId('mail-webhook-url')
        await expect(addresses).toHaveCount(2)
        await expect(addresses.first()).toContainText('/mail/brevo')
        await expect(addresses.nth(1)).toContainText('/mail/brevo')
    })

    /**
     * A test mail goes wherever it is pointed. Whether a relay delivers is frequently a question
     * about somebody else's mailbox, so the field starts at the address of whoever is looking and
     * does not stay there.
     */
    test('a test mail can be aimed at any address', async ({managerPage: page}) => {
        await page.goto(MAILING)

        const recipient = page.getByLabel('Testmail an').first()
        await expect(recipient).not.toHaveValue('')

        await recipient.fill('jemand.anderes@example.org')
        await page.getByRole('button', {name: 'Testmail senden'}).first().click()

        await expect(page.getByText(/jemand\.anderes@example\.org|hat abgelehnt/)).toBeVisible()
    })

    /**
     * Emptying the list is its own act, not something a save can do. A save that arrives empty is
     * far more often a client that failed to load the list than somebody meaning to stop sending,
     * and the difference is not recoverable, so the server refuses it and this is the way through.
     */
    test('the list is emptied through the deliberate route', async ({managerPage: page}) => {
        await page.goto(MAILING)

        await page.getByRole('button', {name: 'Alle entfernen'}).click()
        await page.getByRole('button', {name: 'Alle Anbieter entfernen'}).click()

        await expect(page.getByText(/Alle Anbieter entfernt/)).toBeVisible()
        await page.reload()
        await expect(page.getByText(/Noch kein Anbieter eingetragen/)).toBeVisible()
    })

    /**
     * The queue was recorded from the start and could not be read. What matters is that the page
     * says where the post stands, and which provider it is standing at.
     */
    test('the state of the queue is on the page', async ({managerPage: page}) => {
        await page.goto(MAILING)

        await expect(page.getByRole('heading', {name: 'Zustellung'})).toBeVisible()
        await expect(page.getByText('Wartet', {exact: true})).toBeVisible()
        await expect(page.getByText('Hängengeblieben', {exact: true})).toBeVisible()
        await expect(page.getByLabel('Nach Empfänger oder Betreff suchen')).toBeVisible()
        await expect(page.getByLabel('Nach Zustellstatus filtern')).toBeVisible()
    })

    test('a member reaches none of it', async ({memberPage: page}) => {
        await page.goto(MAILING)

        await expect(page.getByTestId('mail-providers-save')).toHaveCount(0)
    })
})
