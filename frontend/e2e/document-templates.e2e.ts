/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Locator, Page} from '@playwright/test'
import {test, expect} from './fixtures/auth'
import {ownMember} from './fixtures/ownMember'
import {unique} from './fixtures/unique'

/**
 * Adds a row of the given number of columns at the top of a block editor.
 *
 * @param area    the part of the letter the editor writes
 * @param columns how many columns the row has
 */
async function addRow(area: Locator, columns: number) {
    await area.getByRole('button', {name: 'Zeile hinzufügen'}).first().click()
    await area.page().getByTestId(`add-row-columns-${columns}`).click()
}

/**
 * Writes a text block: picks the text kind in an empty block, opens its editor and types, putting the
 * placeholders in with the picker above the text, found by searching for their key.
 *
 * @param area  the part of the letter the editor writes
 * @param parts the words, and the placeholders by their key written as `{key}`
 */
async function writeText(area: Locator, parts: string[]) {
    await area.getByRole('button', {name: 'Text', exact: true}).first().click()
    await area.getByRole('button', {name: 'Text bearbeiten'}).last().click()
    const dialog = area.page().getByRole('dialog')
    const text = dialog.locator('.markdown-editor-content .tiptap')
    for (const part of parts) {
        const key = /^\{(.+)}$/.exec(part)?.[1]
        if (key) {
            const picker = dialog.getByTestId('placeholder-picker')
            await picker.getByTestId('placeholder-picker-search').locator('input').fill(key)
            await picker.getByTestId(`placeholder-${key}`).click()
        } else {
            await text.click()
            await area.page().keyboard.press('End')
            await area.page().keyboard.type(part)
        }
    }
    await dialog.getByRole('button', {name: 'Schließen'}).click()
    await expect(dialog).toBeHidden()
}

/**
 * Sets up the certificate of membership the way a station would: a header with the logo beside the
 * station's name, a body with the member's name and a pronoun as chips, open for self service with the
 * usual wait of 30 days.
 *
 * @param page a page of somebody who may write templates
 * @param name what the template is called
 */
async function setUpCertificate(page: Page, name: string) {
    await page.goto('/station/members/templates')
    await page.getByTestId('template-new').click()
    await page.waitForURL(/\/station\/members\/template-editor\/new/)
    await page.getByTestId('template-name').fill(name)

    await page.getByRole('tab', {name: 'Brief'}).click()
    const header = page.getByTestId('letter-header')
    await header.getByTestId('letter-edge-open').click()
    await addRow(header, 2)
    await header.getByRole('button', {name: 'Bild', exact: true}).first().click()
    await header.getByTestId('cell-image-logo').click()
    await writeText(header, ['Jugendfeuerwehr ', '{station.name}'])
    await header.getByTestId('letter-edge-done').click()
    await expect(header.locator('.placeholder-chip')).toHaveCount(1)

    const body = page.getByTestId('letter-body')
    await addRow(body, 1)
    await writeText(body, [
        'Hiermit bestätigen wir, dass ', '{member.fullName}', ' aktives Mitglied ist. ',
        '{pronoun.subject.start}', ' nimmt regelmäßig teil.',
    ])
    await expect(body.locator('.placeholder-chip')).toHaveCount(2)

    await page.getByRole('tab', {name: 'Selbst erstellen'}).click()
    await page.getByTestId('template-self-service').getByRole('switch').click()
    await expect(page.getByTestId('template-cooldown')).toHaveValue('30')

    await page.getByTestId('template-save').click()
    await page.waitForURL(/\/station\/members\/template-editor\/\d+/)
    await expect(page.getByText('Version 1')).toBeVisible()
}

/**
 * Document templates: a letter set up once in Ember, turned into a PDF for a member and filed with
 * them, by a manager from the member page and by the member themselves from their profile.
 */
test.describe('Document templates', () => {

    test('a certificate is set up, generated for a member and through self service until the wait', async ({managerPage, memberPage}) => {
        const name = unique('Bescheinigung')
        await setUpCertificate(managerPage, name)

        await managerPage.getByRole('tab', {name: 'Vorschau'}).click()
        await managerPage.getByTestId('template-preview').click()
        await expect(managerPage.getByTestId('generated-preview')).toBeVisible()

        const member = await ownMember(managerPage, 'Zertifikat')
        await managerPage.goto(`/station/members/detail/${member.memberId}`)
        await managerPage.getByRole('tab', {name: 'Dokumente'}).first().click()
        await managerPage.getByTestId('document-generate-open').click()
        const dialog = managerPage.getByTestId('generate-document-modal')
        await dialog.getByTestId('generate-template').selectOption({label: name})
        await expect(dialog.getByTestId('generated-preview')).toBeVisible()
        await dialog.getByTestId('generate-file').click()
        await expect(managerPage.getByText('Dokument erstellt und abgelegt.')).toBeVisible()
        await expect(managerPage.getByText(name).first()).toBeVisible()

        await memberPage.goto('/station/profile')
        const offer = memberPage.getByTestId('self-service-offer').filter({hasText: name})
        await offer.getByTestId('self-service-generate').click()
        await expect(memberPage.getByText(name).first()).toBeVisible()
        await expect(offer.getByText(/Wieder möglich ab/)).toBeVisible()

        await offer.getByTestId('self-service-generate').click()
        await expect(memberPage.getByTestId('self-service-documents')
            .getByText(/kann wieder erstellt werden ab/)).toBeVisible()
    })
})
