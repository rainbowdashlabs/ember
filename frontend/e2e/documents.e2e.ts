/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect, apiHeaders} from './fixtures/auth'
import {createIdentifiedMember} from './fixtures/member'
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
    await page.getByRole('tab', {name: 'Dokumente'}).first().click()
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
     * Clearing out is choosing several documents and removing them in one go, after one question. The
     * filter for the paperwork of people who have left is what it is usually done from.
     */
    test('chosen documents are removed in one go after a confirmation', async ({managerPage: page}) => {
        const title = unique('Altlast')

        await page.goto('/station/members/documents')
        await expect(page.getByTestId('documents-departed')).toBeVisible()
        await uploadDocument(page, title, 'altlast.txt', 'Kann weg.')

        await page.getByPlaceholder('Titel oder Inhalt').fill(title)
        const tile = page.getByTestId('document-tile').filter({hasText: title})
        await expect(tile).toHaveCount(1)
        await page.getByTestId('document-select').first().check()

        await page.getByTestId('documents-prune').click()
        await page.getByTestId('documents-prune-confirm').click()

        await expect(tile).toHaveCount(0)
    })

    /**
     * A document kept for the record outlives the member it is about, and still says whose it is.
     *
     * The member is deleted rather than archived, so nobody is left on the document to name: their name is
     * what stays, marked as deleted. The switch for the paperwork of people who left finds it, and leaves the
     * station's own paperwork out, which is the difference that makes clearing out safe.
     */
    test('a kept document names the deleted member and is found among those who left',
        async ({managerPage: page}) => {
            const {surname, id: memberId} = await createIdentifiedMember(page)
            const headers = await apiHeaders(page)

            const base = unique('Nachweis')
            const keptTitle = `${base} behalten`
            const kept = await page.request.post(`/api/v1/station-members/${memberId}/documents`, {
                headers,
                multipart: {
                    file: {name: 'nachweis.txt', mimeType: 'text/plain', buffer: Buffer.from('Fuer die Akten.')},
                    title: keptTitle,
                    keepOnArchive: 'true',
                },
            })
            expect(kept.ok(), `the document was kept for the member (${await kept.text()})`).toBeTruthy()

            const ownPaper = `${base} Wache`
            await page.goto('/station/members/documents')
            await uploadDocument(page, ownPaper, 'wache.txt', 'Gehoert der Wache.')

            const deleted = await page.request.delete(`/api/v1/station-members/${memberId}`, {headers})
            expect(deleted.ok(), `the member was deleted (${await deleted.text()})`).toBeTruthy()

            await page.goto('/station/members/documents')
            await page.getByPlaceholder('Titel oder Inhalt').fill(base)
            const keptTile = page.getByTestId('document-tile').filter({hasText: keptTitle})
            await expect(keptTile, 'the kept document is still there').toBeVisible()
            await expect(keptTile.getByTestId('document-departed'), 'and names whose it was, marked as deleted')
                .toContainText(new RegExp(`${surname}.*\\(gelöscht\\)`))
            await expect(page.getByTestId('document-tile').filter({hasText: ownPaper})).toBeVisible()

            const departedOnly = page.getByTestId('documents-departed')
            await expect(async () => {
                if (await departedOnly.getAttribute('aria-pressed') !== 'true') await departedOnly.click()
                await expect(page.getByTestId('document-tile').filter({hasText: ownPaper})).toHaveCount(0)
            }).toPass({timeout: 30000})
            await expect(keptTile, 'the switch for those who left finds it').toBeVisible()
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
