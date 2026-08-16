/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique, uniqueKey} from './fixtures/unique'

/**
 * The ticket story creates its own board rather than reaching for a seeded one: a ticket is
 * addressed through its board, and two stories sharing a board would be two stories writing into
 * the same lanes.
 */
test.describe('Boards', () => {
    test('a board is created and opens', async ({managerPage: page}) => {
        const board = unique('Board')
        const key = uniqueKey()

        await page.goto('/station/boards/manage')
        await page.getByRole('button', {name: 'Board erstellen'}).click()

        const fields = page.getByRole('textbox')
        await fields.nth(0).fill(board)
        await fields.nth(1).fill(key)
        await page.getByRole('button', {name: 'Erstellen'}).last().click()

        await page.waitForURL(new RegExp(`/station/boards/${key}`, 'i'))
        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    /** A ticket carries the conversation about it, which is most of what a board is for. */
    test('a ticket takes a comment', async ({managerPage: page}) => {
        const board = unique('Board')
        const key = uniqueKey()
        const ticket = unique('Ticket')
        const comment = unique('Kommentar')

        await page.goto('/station/boards/manage')
        await page.getByRole('button', {name: 'Board erstellen'}).click()
        const fields = page.getByRole('textbox')
        await fields.nth(0).fill(board)
        await fields.nth(1).fill(key)
        await page.getByRole('button', {name: 'Erstellen'}).last().click()
        await page.waitForURL(new RegExp(`/station/boards/${key}`, 'i'))

        await page.goto(`/station/boards/${key}/tickets/new`)
        await page.getByRole('textbox').first().fill(ticket)
        const description = page.locator('[contenteditable="true"]').first()
        await description.click()
        await page.keyboard.type('Von der Story angelegt.')
        await page.getByRole('button', {name: 'Erstellen', exact: true}).click()

        await page.getByRole('button', {name: /Kommentare/}).click()
        // The comment box is a rich text editor, so its placeholder is text on the page rather
        // than an attribute, and it takes typing rather than a fill.
        await page.locator('[contenteditable="true"]').last().click()
        await page.keyboard.type(comment)
        await page.getByRole('button', {name: 'Absenden'}).click()

        await expect(page.getByText(comment).first()).toBeVisible()
    })

    test('a ticket is created on a board', async ({managerPage: page}) => {
        const board = unique('Board')
        const key = uniqueKey()
        const ticket = unique('Ticket')

        await page.goto('/station/boards/manage')
        await page.getByRole('button', {name: 'Board erstellen'}).click()
        const fields = page.getByRole('textbox')
        await fields.nth(0).fill(board)
        await fields.nth(1).fill(key)
        await page.getByRole('button', {name: 'Erstellen'}).last().click()
        await page.waitForURL(new RegExp(`/station/boards/${key}`, 'i'))

        await page.goto(`/station/boards/${key}/tickets/new`)
        await page.getByRole('textbox').first().fill(ticket)

        const description = page.locator('[contenteditable="true"]').first()
        await description.click()
        await page.keyboard.type('Von der Story angelegt.')

        await page.getByRole('button', {name: 'Erstellen', exact: true}).click()

        await expect(page.getByText(ticket).first()).toBeVisible()
    })
})
