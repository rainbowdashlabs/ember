/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect, apiHeaders} from './fixtures/auth'
import {ownCluster} from './fixtures/cluster'
import {sidebarEntry} from './fixtures/sidebar'

/** The words a sidebar entry carries with its count, the way a screen reader reads them. */
function labelled(label: string, count: number): string {
    return count > 0 ? `${label} ${count}` : label
}

/** How many unread notices the server holds for the reader right now. */
async function unreadNow(page: Page, headers: Record<string, string>): Promise<number> {
    const answer = await page.request.get('/api/v1/sidebar-counts', {headers})
    expect(answer.ok(), `the counts could be read (${await answer.text()})`).toBeTruthy()
    return (await answer.json()).notifications ?? 0
}

/**
 * The numbers beside the sidebar entries follow the lists behind them without a reload.
 *
 * <p>The seeded station is shared by every story running at the same moment, so a story there cannot
 * hold a count to a fixed number. It holds the sidebar to what the server counts after its own action
 * instead, well before the minute after which the counts are read again anyway. The waiting list story has a station of its own and holds the badge to exact numbers.
 */
test.describe('Sidebar counts', () => {
    /**
     * Opening a notice marks it read, and the overview's count is read again at once and shown, with
     * no reload in between.
     */
    test('marking a notice read lowers the overview count without a reload', async ({managerPage, memberPage}) => {
        const managerHeaders = await apiHeaders(managerPage)
        const memberHeaders = await apiHeaders(memberPage)
        const title = `Zähler ${test.info().workerIndex}-${Date.now()}`

        const session = await memberPage.request.get('/api/v1/session', {headers: memberHeaders})
        expect(session.ok(), `the member has a session to be named by (${await session.text()})`).toBeTruthy()
        const reader = await session.json()
        const mention = `@[${reader.stationId}/${reader.member.uid}:Mitglied]`

        const article = await managerPage.request.post('/api/v1/news', {
            headers: managerHeaders,
            data: {title, contentMarkdown: 'Zum Zählen.', userTypes: [], groupIds: [], tagIds: [], memberIds: []},
        })
        expect(article.ok(), `the organiser wrote an article (${await article.text()})`).toBeTruthy()
        const newsId = (await article.json()).id
        const comment = await managerPage.request.post(`/api/v1/news/${newsId}/comments`, {
            headers: managerHeaders,
            data: {parentId: null, content: `${mention} schau mal`},
        })
        expect(comment.ok(), `the organiser named the member (${await comment.text()})`).toBeTruthy()

        try {
            await memberPage.goto('/station/dashboard/overview')
            const notice = memberPage.getByTestId('notification-entry')
                .filter({hasText: title})
                .filter({hasText: 'erwähnt'})
            await expect(notice).toHaveCount(1, {timeout: 15_000})

            const acknowledged = memberPage.waitForResponse(response =>
                /\/notifications\/\d+\/acknowledge$/.test(response.url()) && response.ok())
            await notice.click()
            await acknowledged

            await expect(async () => {
                const unread = await unreadNow(memberPage, memberHeaders)
                await expect(sidebarEntry(memberPage, 'Übersicht'), 'the overview follows the notice read')
                    .toHaveAccessibleName(labelled('Übersicht', unread), {timeout: 1_000})
            }).toPass({timeout: 10_000})
        } finally {
            await managerPage.request.delete(`/api/v1/news/${newsId}`, {headers: managerHeaders})
        }
    })

    /**
     * A new entry raises the waiting list count, and opening the list takes it off again for the
     * reader, without a reload. The station is the story's own, so nobody else adds to the count.
     */
    test('opening a waiting list clears its new entries from the count', async ({adminPage, browser, request}) => {
        const own = await ownCluster(adminPage, browser, request, 'Wartezähler')
        const page = own.stationPage
        const headers = await apiHeaders(page)

        const list = await page.request.post('/api/v1/waiting-lists', {headers, data: {name: 'Zählliste'}})
        expect(list.ok(), `the station made a list (${await list.text()})`).toBeTruthy()
        const listId = (await list.json()).id
        const entered = await page.request.post(`/api/v1/waiting-lists/${listId}/entries`, {
            headers,
            data: {firstname: 'Neu', lastname: `Wartend-${Date.now()}`},
        })
        expect(entered.ok(), `somebody stands on the list (${await entered.text()})`).toBeTruthy()

        await page.goto('/station/members/list')
        await expect(sidebarEntry(page, 'Wartelisten'), 'the new entry is counted')
            .toHaveAccessibleName('Wartelisten 1')

        const seen = page.waitForResponse(response =>
            response.url().endsWith(`/api/v1/waiting-lists/${listId}/seen`) && response.ok())
        await sidebarEntry(page, 'Wartelisten').click()
        await page.getByRole('link', {name: /Zählliste/}).click()
        await seen

        await expect(sidebarEntry(page, 'Wartelisten'), 'opening the list took it off the count')
            .toHaveAccessibleName('Wartelisten')
    })
})
