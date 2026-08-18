/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * A catalogue of its own, because everything else in the quiz hangs off one: a story that added
 * its category or its question to a seeded catalogue would be reading whatever the story before it
 * left behind.
 */
async function createCatalogue(page: Page): Promise<string> {
    const catalogue = unique('Katalog')

    await page.goto('/station/quiz/catalogs')
    await page.getByRole('button', {name: 'Neuer Katalog'}).click()
    await page.getByRole('textbox').first().fill(catalogue)
    await page.getByRole('button', {name: /Speichern|Erstellen|Anlegen/}).last().click()

    await expect(page.getByText(catalogue).first()).toBeVisible()
    return catalogue
}

/** Opens the catalogue by its name, which is what the list offers to click. */
async function openCatalogue(page: Page, catalogue: string): Promise<void> {
    await page.getByText(catalogue).first().click()
    await page.waitForURL(/\/station\/quiz\/catalogs\/\d+/)
}

/** Both halves of the quiz are open to whoever configures them. */
test.describe('Quiz', () => {
    test('a question catalogue is created', async ({managerPage: page}) => {
        await createCatalogue(page)
    })

    /** A catalogue opens on its categories, which is where its questions live. */
    test('a created catalogue opens', async ({managerPage: page}) => {
        await openCatalogue(page, await createCatalogue(page))

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    /** A catalogue holds its questions in categories, so a fresh one starts by getting one. */
    test('a category is added to a catalogue', async ({managerPage: page}) => {
        const category = unique('Kategorie')

        await openCatalogue(page, await createCatalogue(page))

        await page.getByRole('button', {name: 'Neue Kategorie'}).click()
        await page.getByPlaceholder('Kategoriename').fill(category)
        await page.getByRole('button', {name: /Speichern|Erstellen|Anlegen|Neue Kategorie/}).last().click()

        await expect(page.getByText(category).first()).toBeVisible()
    })

    /**
     * A catalogue without questions asks nothing, so this is the story the whole feature rests on.
     * The question is written and the page reloaded: what the editor still shows after a reload is
     * what actually reached the catalogue.
     */
    test('a question is added to a catalogue and stays there', async ({managerPage: page}) => {
        const question = unique('Frage')

        await openCatalogue(page, await createCatalogue(page))

        await page.getByRole('button', {name: 'Neue Frage'}).click()
        await page.getByPlaceholder('Fragetext').fill(question)
        await page.getByRole('button', {name: 'Speichern'}).last().click()

        await expect(page.getByText(question).first()).toBeVisible()

        await page.reload()
        await expect(page.getByText(question).first()).toBeVisible()
    })

    /**
     * A test sheet is assembled rather than written: it says how many questions to draw from which
     * catalogue, and the questions are picked when somebody sits it. The story builds one section
     * over the catalogue it just filled and finds the sheet again in the list afterwards.
     */
    test('a test sheet is assembled from a catalogue', async ({managerPage: page}) => {
        const question = unique('Frage')
        const sheet = unique('Test')
        const section = unique('Abschnitt')

        const catalogue = await createCatalogue(page)
        await openCatalogue(page, catalogue)
        await page.getByRole('button', {name: 'Neue Frage'}).click()
        await page.getByPlaceholder('Fragetext').fill(question)
        await page.getByRole('button', {name: 'Speichern'}).last().click()
        await expect(page.getByText(question).first()).toBeVisible()

        await page.goto('/station/quiz/tests/create')
        await page.getByPlaceholder('Name des Tests').fill(sheet)

        await page.getByRole('button', {name: 'Abschnitt hinzufügen'}).click()
        await page.getByPlaceholder('Titel des Abschnitts').fill(section)

        // A section draws its questions from sources, and a fresh one has none yet. The catalogue
        // belongs to the select that offers choosing one, which is what tells it apart from the
        // category select beside it.
        await page.getByRole('button', {name: 'Quelle hinzufügen'}).click()
        await page.locator('select:has(option:text-is("Katalog wählen"))').first()
            .selectOption({label: catalogue})

        await page.getByRole('button', {name: 'Speichern'}).click()

        await page.goto('/station/quiz/tests')
        await expect(page.getByText(sheet).first()).toBeVisible()
    })

    /**
     * A catalogue is often already written down somewhere else, so it can arrive as a file. The
     * story walks the whole wizard - file, columns, preview, import - because each step can refuse
     * on its own, and then looks for the imported question in the catalogue itself.
     */
    test('questions are imported from a file', async ({managerPage: page}) => {
        const question = unique('Importfrage')

        const catalogue = await createCatalogue(page)
        await openCatalogue(page, catalogue)
        await page.goto(`${page.url()}/import`)

        await page.setInputFiles('input[type="file"]', {
            name: 'fragen.csv',
            mimeType: 'text/csv',
            // Commas, because that is the separator the wizard offers to start with.
            buffer: Buffer.from(`Frage,Antwort\n${question},Eine Antwort\n`, 'utf-8'),
        })

        await page.getByRole('button', {name: 'Weiter'}).click()

        await page.locator('select:has(option:text-is("Frage"))').first().selectOption({label: 'Frage'})
        await page.getByRole('button', {name: 'Vorschau'}).click()

        await page.getByRole('button', {name: 'Importieren'}).click()

        await expect(page.getByText(/1 Fragen importiert/)).toBeVisible()

        await page.goto('/station/quiz/catalogs')
        await openCatalogue(page, catalogue)
        await expect(page.getByText(question).first()).toBeVisible()
    })

    test('the test sheets are reachable', async ({managerPage: page}) => {
        await page.goto('/station/quiz/tests')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        expect(page.url()).toContain('/station/quiz/tests')
    })

    /**
     * The point of a test sheet is somebody sitting it. The seeded station carries an active one,
     * and a member is offered it directly in the list - the row navigates by click handler, so it
     * carries an identifier for the story to aim at.
     *
     * The paper is handed in unanswered on purpose: what the story holds is that a member can sit
     * a test and give it back, not that they know the answers.
     */
    test('a member sits an active test and hands it in', async ({memberPage: page}) => {
        await page.goto('/station/quiz/tests')

        const row = page.getByTestId('test-entry').first()
        await expect(row).toBeVisible()

        await row.getByRole('button', {name: 'Test schreiben'}).click()

        await page.waitForURL(/\/station\/quiz\/tests\/\d+\/take/)

        // Handing in asks once more, with the same words on the confirming button.
        await page.getByRole('button', {name: 'Test abgeben'}).first().click()
        await page.getByRole('button', {name: 'Test abgeben'}).last().click()

        await expect(page.getByText('Abgegeben').first()).toBeVisible()
    })

    /** Training asks questions without recording an attempt, so it is open to everyone. */
    test('a member reaches the training', async ({memberPage: page}) => {
        await page.goto('/station/quiz/training')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
