/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect, apiHeaders} from './fixtures/auth'
import {unique, uniqueKey} from './fixtures/unique'

/**
 * Every story creates its own board rather than reaching for a seeded one: a ticket is addressed
 * through its board, and two stories sharing a board would be two stories writing into the same
 * lanes.
 */
async function createBoard(page: Page): Promise<string> {
    const board = unique('Board')
    const key = uniqueKey()

    await page.goto('/station/boards/manage')
    await page.getByRole('button', {name: 'Board erstellen'}).click()

    const fields = page.getByRole('textbox')
    await fields.nth(0).fill(board)
    await fields.nth(1).fill(key)
    await page.getByRole('button', {name: 'Erstellen'}).last().click()

    await page.waitForURL(new RegExp(`/station/boards/${key}`, 'i'))
    return key
}

/** A ticket on that board, left open on its own page - which is where creating one lands. */
async function createTicket(page: Page, key: string): Promise<string> {
    const ticket = unique('Ticket')

    await page.goto(`/station/boards/${key}/tickets/new`)
    await page.getByRole('textbox').first().fill(ticket)

    const description = page.locator('[contenteditable="true"]').first()
    await description.click()
    await page.keyboard.type('Von der Story angelegt.')

    await page.getByRole('button', {name: 'Erstellen', exact: true}).click()
    await expect(page.getByText(ticket).first()).toBeVisible()
    return ticket
}

test.describe('Boards', () => {
    test('a board is created and opens', async ({managerPage: page}) => {
        await createBoard(page)

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    test('a ticket is created on a board', async ({managerPage: page}) => {
        const key = await createBoard(page)

        await createTicket(page, key)
    })

    /** A ticket carries the conversation about it, which is most of what a board is for. */
    test('a ticket takes a comment', async ({managerPage: page}) => {
        const comment = unique('Kommentar')

        const key = await createBoard(page)
        await createTicket(page, key)

        await page.getByRole('button', {name: /Kommentare/}).click()
        // The comment box is a rich text editor, so its placeholder is text on the page rather
        // than an attribute, and it takes typing rather than a fill.
        await page.locator('[contenteditable="true"]').last().click()
        await page.keyboard.type(comment)
        await page.getByRole('button', {name: 'Absenden'}).click()

        await expect(page.getByText(comment).first()).toBeVisible()
    })

    /**
     * Work moving along is what a board is. The lane is changed from the ticket rather than by
     * dragging its card: the same call sits behind both, and a story that drags would be measuring
     * the mouse.
     */
    test('a ticket moves to another lane and stays there', async ({managerPage: page}) => {
        const key = await createBoard(page)
        await createTicket(page, key)

        await page.getByText('Offen', {exact: true}).first().click()
        await page.getByText('In Arbeit', {exact: true}).first().click()

        await expect(page.getByText('In Arbeit', {exact: true}).first()).toBeVisible()

        await page.reload()
        await expect(page.getByText('In Arbeit', {exact: true}).first()).toBeVisible()
        await expect(page.getByText('Offen', {exact: true})).toHaveCount(0)
    })

    /**
     * A ticket nobody owns is a ticket nobody does. Assigning it is one click on the line that says
     * so, and the name it then carries is what the board shows on the card.
     */
    test('a ticket is assigned to a member', async ({managerPage: page}) => {
        const key = await createBoard(page)
        await createTicket(page, key)

        await page.getByText('Nicht zugewiesen').first().click()

        // Whoever the picker offers first, read off the picker itself: who is in the station changes
        // as the other stories create people, and the story only needs somebody to hand the ticket
        // to. The first entry is "nobody", which is what the ticket already says.
        const candidates = page.getByTestId('ticket-assignee').locator('button').filter({hasNotText: 'Nicht zugewiesen'})
        const candidate = candidates.first()
        await expect(candidate).toBeVisible()
        // The last line of the entry: somebody without a picture is drawn with their initials above
        // their name, and the initials are not what the card carries afterwards.
        const name = (await candidate.innerText()).trim().split('\n').pop()!.trim()

        // Picking a name saves the ticket, and clicking only dispatches the click: it says nothing
        // about the save having gone out. Reloading straight afterwards tears the page down and
        // takes the request with it, so the assignment is lost and the story fails having asked for
        // something nobody ever sent. Waiting for the answer is what makes the reload meaningful.
        const saved = page.waitForResponse(
            response => response.request().method() === 'PUT'
                && /\/tickets\/\d+$/.test(new URL(response.url()).pathname),
        )
        await candidate.click()
        expect((await saved).status()).toBe(200)

        await page.reload()
        // The board is drawn from several requests, and under load the default wait ran out before
        // the last of them landed, which read as an assignment that had not been kept.
        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByText(name).first()).toBeVisible({timeout: 30000})
        await expect(page.getByText('Nicht zugewiesen')).toHaveCount(0)
    })

    /**
     * A ticket often needs a list of its own, and the point of ticking one is that the tick is
     * still there tomorrow.
     */
    test('a ticket carries a checklist whose ticks are kept', async ({managerPage: page}) => {
        const item = unique('Punkt')

        const key = await createBoard(page)
        await createTicket(page, key)

        await page.getByRole('button', {name: 'Add', exact: true}).first().click()
        await page.getByText('Checkliste').last().click()

        await page.getByPlaceholder('Punkt hinzufügen').fill(item)
        await page.getByRole('button', {name: 'Hinzufügen'}).first().click()
        await expect(page.getByText(item)).toBeVisible()

        await page.getByRole('checkbox').first().click()

        await page.reload()
        await expect(page.getByRole('checkbox').first()).toBeChecked()
    })

    /**
     * The backlog is where work waits that nobody has planned yet. It is switched on per board, and
     * a ticket put there leaves the lanes and is found on the backlog page.
     */
    test('a ticket put in the backlog leaves the board and is found there', async ({managerPage: page}) => {
        const key = await createBoard(page)
        const ticket = await createTicket(page, key)

        await page.goto(`/station/boards/${key}/settings`)
        // The switch belongs to the line that names it, and the settings page has more than one.
        await page.getByText('Backlog', {exact: true}).locator('xpath=following-sibling::button').click()
        await expect(page.getByText('Gespeichert')).toBeVisible()

        await page.goto(`/station/boards/${key}`)
        await page.getByText(ticket).first().click()
        await page.waitForURL(/\/tickets\/[\w-]+/i)

        await page.getByText('Offen', {exact: true}).first().click()
        await page.getByText('Backlog', {exact: true}).last().click()

        await page.goto(`/station/boards/${key}/backlog`)
        await expect(page.getByText(ticket).first()).toBeVisible()

        await page.goto(`/station/boards/${key}`)
        await expect(page.getByText(ticket)).toHaveCount(0)
    })

    /**
     * Nothing archives a ticket by hand: the archive holds whatever has sat in the last lane longer
     * than the board's own number of days. So the story reads it on a seeded board, whose tickets
     * the seeder ages for exactly this, and holds it to listing them under the board's name.
     */
    test('the archive of a board lists what has been done for a while', async ({managerPage: page}) => {
        await page.goto('/station/boards')

        // The list navigates by click handler rather than by link, as the planner does.
        const board = page.locator('main [class*="cursor-pointer"]').first()
        await expect(board).toBeVisible()
        await board.click()
        await page.waitForURL(/\/station\/boards\/[^/]+$/)

        await page.goto(`${page.url()}/archived`)
        await expect(page.getByText(/archiviert/).first()).toBeVisible()
    })

    /**
     * A ticket is addressed by its board and its number, so a notice about a mention in one of its
     * comments has to name both. The story presses the notice instead of reading its address: a
     * link that names the ticket by its id alone leaves the address unbuildable, and that is only
     * visible on the press. Where the press lands is then opened by somebody who may read the
     * board, which is what shows the address really is the ticket's own.
     */
    test('a mention in a ticket comment leads to the ticket', async ({managerPage, memberPage}) => {
        const managerHeaders = await apiHeaders(managerPage)
        const memberHeaders = await apiHeaders(memberPage)

        const session = await memberPage.request.get('/api/v1/session', {headers: memberHeaders})
        expect(session.ok(), `the member has a session to be named by (${await session.text()})`).toBeTruthy()
        const reader = await session.json()
        const mention = `@[${reader.stationId}/${reader.member.uid}:Mitglied]`

        const key = await createBoard(managerPage)
        const ticket = await createTicket(managerPage, key)
        await managerPage.waitForURL(/\/tickets\/\d+/)
        const ticketNumber = Number(managerPage.url().match(/\/tickets\/(\d+)/)![1])

        const written = await managerPage.request.post(`/api/v1/boards/${key}/tickets/${ticketNumber}/comments`, {
            headers: managerHeaders,
            data: {parentId: null, content: `${mention} sieh dir das an`},
        })
        expect(written.ok(), `the organiser wrote a comment (${await written.text()})`).toBeTruthy()
        const commentId = (await written.json()).id

        await memberPage.goto('/station/dashboard/overview')
        const notice = memberPage.getByTestId('notification-entry').filter({hasText: `${key}-${ticketNumber}`})
        await expect(notice).toHaveCount(1, {timeout: 15000})

        await notice.click()
        await memberPage.waitForURL(
            new RegExp(`/station/boards/${key}/tickets/${ticketNumber}\\?.*comment=${commentId}`, 'i'))

        await managerPage.goto(memberPage.url())
        await expect(managerPage.getByText(ticket).first()).toBeVisible({timeout: 30000})
    })

    /** Boards are not open to everyone: whoever may not use them is offered none. */
    test('a member without the right reaches no board', async ({memberPage: page}) => {
        await page.goto('/station/boards')

        await expect(page.getByRole('button', {name: 'Board erstellen'})).toHaveCount(0)
    })
})
