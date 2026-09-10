/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * Puts a document in through the upload button the page in front of us offers. The dialog is the same
 * one on the station page and on a member's profile, so the stories differ only in where they stand
 * when they open it.
 */
async function uploadDocument(page: Page, title: string, fileName: string, says: string) {
    await page.getByRole('button', {name: 'Hochladen'}).first().click()

    const dialog = page.getByRole('dialog')
    await dialog.locator('input[type="file"]').setInputFiles({
        name: fileName,
        mimeType: 'text/plain',
        buffer: Buffer.from(says),
    })
    await dialog.getByPlaceholder('Wie das Dokument heißen soll').fill(title)
    await dialog.getByRole('button', {name: 'Hochladen'}).click()

    await expect(page.getByText(title).first()).toBeVisible()
}

/**
 * Opens the first member's document tab, which is where a document about one person belongs.
 */
async function openFirstMembersDocuments(page: Page) {
    await page.goto('/station/members/list')
    await page.getByTestId('member-row').first().getByRole('button', {name: 'Details'}).click()
    await page.waitForURL(/\/station\/members\/detail\/\d+/)
    await page.getByRole('button', {name: 'Dokumente'}).first().click()
}

/**
 * The document store. A document is a file kept for the members it concerns, so every story here
 * puts one in and then looks for it where somebody would go for it.
 */
test.describe('Documents', () => {

    /**
     * The store of the whole station, which is where a document that concerns nobody in particular
     * belongs. It is put in with a title, because a file name is not what anybody calls a document.
     */
    test('a document is put in the store and found by its title', async ({managerPage: page}) => {
        const title = unique('Vertrag')

        await page.goto('/station/members/documents')
        await uploadDocument(page, title, 'vertrag.txt', 'Diese Vereinbarung gilt ab sofort.')

        await page.getByPlaceholder('Titel oder Inhalt').fill(title)
        await expect(page.getByTestId('document-tile').first()).toBeVisible()
        await expect(page.getByText(title).first()).toBeVisible()
    })

    /**
     * What can be read out of a document is searched too, which is the point of keeping the text:
     * nobody remembers what they called a file, but they remember what was in it.
     */
    test('a document is found by what it says rather than by its name', async ({managerPage: page}) => {
        const word = unique('Loeschzug').replace(/-/g, '')
        const title = unique('Protokoll')

        await page.goto('/station/members/documents')
        await uploadDocument(page, title, 'protokoll.txt', `Anwesend war der ${word} in voller Staerke.`)

        await page.getByPlaceholder('Titel oder Inhalt').fill(word)
        await expect(page.getByText(title).first()).toBeVisible()
    })

    /**
     * A document on a member is what the profile tab is for. The story puts one there and opens it,
     * which is also the only way to see it rather than download it.
     */
    test('a document on a member is opened from their profile', async ({managerPage: page}) => {
        const title = unique('Einverstaendnis')

        await openFirstMembersDocuments(page)
        await uploadDocument(page, title, 'einverstaendnis.txt', 'Hiermit erteile ich mein Einverstaendnis.')

        await page.getByTestId('document-tile').first().click()
        await expect(page.getByRole('dialog').getByText('Hiermit erteile ich mein Einverstaendnis.')).toBeVisible()
    })

    /**
     * The station's own paperwork is what the store holds besides the members' documents, and mixed
     * together the one is lost among the other. Two documents go in, one on a member and one on
     * nobody, and the switch has to tell them apart.
     */
    test('the station\'s own paperwork is shown without the members\' documents', async ({managerPage: page}) => {
        const ownPaper = unique('Pruefbescheinigung')
        const onAMember = unique('Attest')

        await page.goto('/station/members/documents')
        await uploadDocument(page, ownPaper, 'pruefung.txt', 'Die Leiter wurde geprueft.')

        await openFirstMembersDocuments(page)
        await uploadDocument(page, onAMember, 'attest.txt', 'Bescheinigung ueber die Untersuchung.')

        await page.goto('/station/members/documents')
        await expect(page.getByText(ownPaper).first()).toBeVisible()
        await expect(page.getByText(onAMember).first()).toBeVisible()

        const unboundOnly = page.getByTestId('documents-unbound')
        await expect(async () => {
            if (await unboundOnly.getAttribute('aria-pressed') !== 'true') await unboundOnly.click()
            await expect(page.getByText(onAMember)).toHaveCount(0)
        }).toPass({timeout: 30000})

        await expect(page.getByText(ownPaper).first()).toBeVisible()
    })

    /**
     * Reading one's own documents needs no permission, and a member who was never granted the
     * upload right is offered no way in.
     */
    test('a member sees their own documents without being offered to add any', async ({memberPage: page}) => {
        await page.goto('/station/profile')

        await expect(page.getByText('Dokumente').first()).toBeVisible()
        await expect(page.getByRole('button', {name: 'Hochladen'})).toHaveCount(0)
    })
})
