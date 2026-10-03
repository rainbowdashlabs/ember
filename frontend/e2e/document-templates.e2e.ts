/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect} from './fixtures/auth'
import {ownMember} from './fixtures/ownMember'
import {unique} from './fixtures/unique'

/**
 * Sets up the certificate of membership the way a station would: a letterhead with the logo and two
 * lines of text, a body with the member's name and a pronoun as chips, open for self service with
 * the usual wait of 30 days.
 *
 * @param page a page of somebody who may write templates
 * @param name what the template is called
 */
async function setUpCertificate(page: Page, name: string) {
    await page.goto('/station/members/templates')
    await page.getByTestId('template-new').click()
    await page.waitForURL(/\/station\/members\/template-editor\/new/)
    await page.getByTestId('template-name').fill(name)

    await page.getByRole('tab', {name: 'Briefkopf'}).click()
    const header = page.getByTestId('letter-header')
    await header.getByTestId('letter-cell-add').click()
    await header.getByTestId('letter-cell-kind').last().selectOption('LOGO')
    await header.getByTestId('letter-cell-add').click()
    await header.getByTestId('letter-cell-kind').last().selectOption('TEXT')
    await header.getByTestId('letter-cell-text').fill('Jugendfeuerwehr\nBescheinigung der Wache')
    await header.getByTestId('placeholder-picker').last().selectOption('station.name')

    await page.getByRole('tab', {name: 'Text'}).click()
    const body = page.locator('.markdown-editor-content .tiptap')
    await body.click()
    await page.keyboard.type('Hiermit bestätigen wir, dass ')
    await page.getByTestId('placeholder-picker').first().selectOption('member.fullName')
    await body.click()
    await page.keyboard.press('End')
    await page.keyboard.type(' aktives Mitglied ist. ')
    await page.getByTestId('placeholder-picker').first().selectOption('pronoun.subject.start')
    await body.click()
    await page.keyboard.press('End')
    await page.keyboard.type(' nimmt regelmäßig teil.')
    await expect(page.locator('.markdown-editor-content .placeholder-chip')).toHaveCount(2)

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
