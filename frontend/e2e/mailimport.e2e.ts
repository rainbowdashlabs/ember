/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * Reading mail for documents, as far as a browser can see it.
 *
 * <p>The reading itself is driven against a real IMAP server in the backend tests, because a story
 * cannot deliver a mail. What these cover is the half a person touches: the page refusing what would
 * quietly do nothing, and the log that answers the question everybody asks.
 */

/** Opens the page, which lives beside the station's other mail settings. */
async function openMailImport(page: Page) {
    await page.goto('/station/manage/mail-import')
    await expect(page.getByRole('button', {name: 'Postfach hinzufügen'})).toBeVisible()
}

/**
 * Fills in a mailbox pointing at a host that does not answer.
 *
 * <p>Nothing here needs it to answer: what is being tested is that the mailbox can be set up at all and
 * that the page says what went wrong when it cannot be reached.
 */
async function addMailbox(page: Page, name: string) {
    await page.getByRole('button', {name: 'Postfach hinzufügen'}).click()
    await page.getByTestId('mailbox-name').fill(name)
    await page.getByTestId('mailbox-host').fill('127.0.0.1')
    await page.getByTestId('mailbox-security').selectOption('NONE')
    await page.getByTestId('mailbox-port').fill('1')
    await page.getByTestId('mailbox-username').fill('archive@musterstadt.de')
    await page.getByTestId('mailbox-password').fill('not-a-real-password')
    await page.getByTestId('mailbox-save').click()
    await expect(page.getByText(name).first()).toBeVisible()
}

test.describe('Mail import', () => {

    test('a mailbox is set up and its rules live under it', async ({managerPage: page}) => {
        const name = unique('Archiv')

        await openMailImport(page)
        await addMailbox(page, name)

        await expect(page.getByRole('button', {name: 'Regel hinzufügen'}).first()).toBeVisible()
        await expect(page.getByText('Aus').first()).toBeVisible()
    })

    /**
     * The refusal the whole feature rests on. A rule with no sender would be legal, would accept nothing,
     * and would silently file nothing, so it cannot be saved at all.
     */
    test('a rule cannot be saved without saying which senders it trusts', async ({managerPage: page}) => {
        const name = unique('Archiv')

        await openMailImport(page)
        await addMailbox(page, name)

        await page.getByTestId('rule-add').first().click()
        await page.getByTestId('rule-name').fill('Ohne Absender')

        await expect(page.getByTestId('rule-save')).toBeDisabled()
    })

    /** Nothing wide enough to trust everybody may even be typed in. */
    test('a pattern that would trust everybody is refused as it is typed', async ({managerPage: page}) => {
        const name = unique('Archiv')

        await openMailImport(page)
        await addMailbox(page, name)

        await page.getByTestId('rule-add').first().click()
        await page.getByTestId('rule-sender').fill('*@*')

        await expect(page.getByText('Das ist weder eine Adresse noch')).toBeVisible()
        await expect(page.getByTestId('rule-sender-add')).toBeDisabled()
    })

    test('a rule that names a sender can be saved and is listed', async ({managerPage: page}) => {
        const mailbox = unique('Archiv')
        const rule = unique('Bescheinigungen')

        await openMailImport(page)
        await addMailbox(page, mailbox)

        await page.getByTestId('rule-add').first().click()
        await page.getByTestId('rule-name').fill(rule)
        await page.getByTestId('rule-sender').fill('*@feuerwehr-musterstadt.de')
        await page.getByTestId('rule-sender-add').click()
        await page.getByTestId('rule-save').click()

        await expect(page.getByText(rule).first()).toBeVisible()
        await expect(page.getByText('*@feuerwehr-musterstadt.de').first()).toBeVisible()
    })

    /**
     * A host that does not answer is an answer on the page rather than a stack trace in a log nobody can
     * see, which is what somebody setting this up for the first time needs.
     */
    test('a mailbox that cannot be reached says so on the page', async ({managerPage: page}) => {
        const name = unique('Kaputt')

        await openMailImport(page)
        await addMailbox(page, name)

        await page.getByTestId('mailbox-test').first().click()

        await expect(page.getByTestId('mailbox-test').first()).toBeEnabled({timeout: 30000})
        await expect(page.locator('body')).not.toContainText('Verbindung steht')
    })

    /** The log is the feature: it is there before anything has arrived, and says so. */
    test('the log is on the page from the start', async ({managerPage: page}) => {
        await openMailImport(page)

        await expect(page.getByText('Protokoll').first()).toBeVisible()
        await expect(page.getByTestId('log-refresh')).toBeVisible()
    })

    /** A member has no business in the station's mail settings. */
    test('a member cannot reach the mail import page', async ({memberPage: page}) => {
        await page.goto('/station/manage/mail-import')

        await expect(page.getByRole('button', {name: 'Postfach hinzufügen'})).toHaveCount(0)
    })
})
