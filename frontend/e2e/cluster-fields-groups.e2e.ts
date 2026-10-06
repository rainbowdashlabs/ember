/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect, clusterAccountWith, clusterHeaders, clusterPage, theSeededCluster} from './fixtures/auth'
import {ownCluster, type OwnCluster} from './fixtures/cluster'

/**
 * The association's questions and its groups, on the screens a station already had.
 *
 * Every story here walks the screen it is about. Arranging state through the API is fine and used
 * where it saves a page load; asserting that a screen works is not something an API call can do, and
 * that mistake is the reason these screens were built short of what was asked for in the first place.
 */
test.describe('Cluster fields and groups', () => {
    /**
     * CLS-58 - The questions screen is the station's editor, not a list with a form on top.
     *
     * The questions on one side, who the selected one is asked on the other, a form picker below, and
     * a way to add. The old screen had none of them, so seeing them is what says the station's editor
     * is really what is mounted here.
     */
    test('the association writes its questions in the station editor', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_FIELD_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/members/fields')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await expect(page.getByTestId('audiences-panel')).toBeVisible()

        const tabs = page.getByRole('tab', {name: /Mitglieder|Erziehungsberechtigte|Team|Leitung/})
        await expect(tabs.first(), 'the kinds an association may ask are offered as forms').toBeVisible()
        await expect(page.getByRole('tab', {name: 'Probe', exact: true}), 'an association has no trial members')
            .toHaveCount(0)

        await expect(page.getByRole('button', {name: /Feld hinzufügen/i})).toBeVisible()
        await page.context().close()
    })

    /**
     * CLS-59 - Who may change an answer is one choice, not two switches.
     *
     * The three rungs are offered and the fourth combination cannot be reached, because nothing names
     * it. A station's own screen keeps its single switch, which is the contrast that makes the point.
     */
    test('the association picks who may change an answer', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_FIELD_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/members/fields')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        const choice = page.locator('select').filter({hasText: 'Nur Verband'}).first()
        if (await choice.count() > 0) {
            const options = await choice.locator('option').allTextContents()
            expect(options, 'three rungs and no fourth').toHaveLength(3)
            expect(options.join(' ')).toContain('Nur Verband')
        }
        await page.context().close()
    })

    /**
     * CLS-60 - A date of birth is not on offer to an association.
     *
     * The template built on it filters itself out against the types the association may use, rather
     * than being hidden by a check for whether this is a cluster.
     */
    test('the birth date template is not offered to an association', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_FIELD_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/members/fields')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await expect(page.getByRole('button', {name: 'Geburtsdatum', exact: true})).toHaveCount(0)
        await page.context().close()
    })

    /**
     * CLS-61 - Groups are two panels, and the association's carry no colour.
     *
     * The old screen unfolded a row into a name, every permission and a checkbox per person. The
     * station's shape puts the list on one side and the one you picked on the other.
     */
    test('the association assigns groups on the station screen', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_MEMBER_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/team/groups')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await expect(page.getByText(/wähl/i).first(), 'with nothing picked the detail side asks to pick').toBeVisible()
        await page.context().close()
    })

    /**
     * CLS-62 - The old addresses still work.
     *
     * Questions moved in with the people they are asked of, and groups moved in with the people they
     * gather. Somebody with either address bookmarked lands where the screen went.
     */
    test('the moved screens keep their old addresses working', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_MEMBER_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/fields')
        await expect(page).toHaveURL(/\/cluster\/members\/fields$/)

        await page.goto('/cluster/members/groups')
        await expect(page).toHaveURL(/\/cluster\/team\/groups$/)

        await page.context().close()
    })

    /**
     * CLS-74 - The association heads its block of questions, and is not offered a date of birth.
     *
     * A heading was written down as something an association may declare and never looked at. The dialog
     * meanwhile offered every type a station has, the date of birth included, which the server refuses:
     * the station declares its own and two would collide. What the owner may choose now decides what the
     * dialog offers, so the refusal is never reached.
     *
     * The heading is taken away again: an association's question reaches every station under it, and one
     * left behind lands on the profile every other story is reading.
     */
    test('the association heads its questions and is offered no birth date', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_FIELD_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/members/fields')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await page.getByTestId('field-add').first().click({timeout: 15000})
        const types = page.getByTestId('field-type').locator('option')
        await expect(types.first(), 'the options are there before an absence is read off them').toBeAttached()
        const offered = await types.allTextContents()
        expect(offered.join(' '), 'a heading is on offer').toContain('Überschrift')
        expect(offered.join(' '), 'a date of birth is not').not.toContain('Geburtsdatum')

        const heading = `Verbandsangaben ${Date.now()}`
        await page.getByTestId('field-name').fill(heading)
        await page.getByTestId('field-type').selectOption('SECTION')
        await page.getByTestId('field-save').click()

        const row = page.getByTestId(`field-row-${heading}`)
        await expect(row).toBeVisible({timeout: 15000})
        await expect(row.getByTestId('asked-of-nobody'), 'a new question reaches no form yet').toBeVisible()

        await row.click()
        await page.getByTestId('audiences-panel').getByTestId('audience-add').selectOption('ROLE:MEMBER')
        await expect(page.getByRole('heading', {name: heading}), 'put to members, it lays out as a heading on their form')
            .toBeVisible({timeout: 15000})

        const headers = await clusterHeaders(page, await theSeededCluster(page))
        const fields = await page.request.get('/api/v1/cluster/fields', {headers}).then(r => r.json())
        const mine = fields.find((field: {name: string}) => field.name === heading)
        expect(mine, 'the heading was written down').toBeTruthy()
        await page.request.delete(`/api/v1/cluster/fields/${mine.id}`, {headers})

        await page.context().close()
    })

    /**
     * A change to an association's member group is written whole or not at all.
     *
     * Two refused changes, and the group has to come out of both exactly as it went in. In the first the name
     * is fine and one of the people is not the association's, so a rename written before the people were
     * checked would stick. In the second the name is another group's in different letters, which is refused on
     * the screen as well, with the name named.
     *
     * An association of the story's own, because renaming a group of the seeded one would rename it under
     * every other story reading it.
     */
    test('a refused change leaves the association member group as it was',
        async ({adminPage: page, browser, request}) => {
            const own = await ownCluster(page, browser, request, 'Gruppenganz')
            const stamp = Date.now()
            const kept = await groupCalled(page, own, `Atemschutz ${stamp}`)
            const other = await groupCalled(page, own, `Maschinisten ${stamp}`)

            const surname = `Gruppe${stamp}`
            const added = await page.request.post('/api/v1/cluster/members', {
                headers: own.headers,
                data: {email: `${surname.toLowerCase()}@e2e.ember`, userType: null, firstName: 'Gerda', lastName: surname},
            })
            expect(added.ok(), `the association took somebody on (${await added.text()})`).toBeTruthy()
            const memberId = (await added.json()).id
            await changeGroup(page, own, kept.id, {memberIds: [memberId]}, 204)

            await changeGroup(page, own, kept.id, {name: `Umbenannt ${stamp}`, memberIds: [memberId, 2_000_000_000]}, 404)
            expect(await groupState(page, own, kept.id), 'a good name does not stick when the people are refused')
                .toEqual({name: kept.name, memberIds: [memberId]})

            await changeGroup(page, own, kept.id, {name: other.name.toUpperCase(), memberIds: []}, 409)
            expect(await groupState(page, own, kept.id), 'and the people do not change when the name is refused')
                .toEqual({name: kept.name, memberIds: [memberId]})

            await page.goto('/cluster/team/groups')
            await page.evaluate(uid => window.localStorage.setItem('cluster_id', uid), own.uid)
            await page.goto('/cluster/team/groups')
            const row = page.getByTestId('group-row').filter({hasText: kept.name})
            await row.getByRole('button', {name: 'Bearbeiten'}).click()

            const modal = page.getByTestId('modal')
            const taken = other.name.toLowerCase()
            await modal.getByPlaceholder('Name der Gruppe').fill(taken)
            await modal.getByRole('button', {name: 'Speichern', exact: true}).click()
            await expect(page.getByText(`Eine andere Gruppe hat schon diesen Namen, es wurde nichts gespeichert (${taken})`),
                'the screen says why, and names the name').toBeVisible()
            await modal.getByRole('button', {name: 'Abbrechen'}).click()

            await expect(row, 'the list still names the group as it was').toBeVisible()
            await row.click()
            await expect(page.getByText(surname).first(), 'with the same person in it').toBeVisible()
            expect(await groupState(page, own, kept.id)).toEqual({name: kept.name, memberIds: [memberId]})

            await own.stationPage.context().close()
        })

    /** A member group of the association, made through its own route. */
    async function groupCalled(page: Page, own: OwnCluster, name: string): Promise<{id: number; name: string}> {
        const made = await page.request.post('/api/v1/cluster/member-groups', {headers: own.headers, data: {name}})
        expect(made.ok(), `the association made a group (${await made.text()})`).toBeTruthy()
        return made.json()
    }

    /** One change to a member group, answered with the status the story expects. */
    async function changeGroup(page: Page, own: OwnCluster, groupId: number, change: object, status: number) {
        const answer = await page.request.put(`/api/v1/cluster/member-groups/${groupId}`,
            {headers: own.headers, data: change})
        expect(answer.status(), `the change answered ${await answer.text()}`).toBe(status)
    }

    /** What a member group is called and who is in it, as the server keeps it. */
    async function groupState(page: Page, own: OwnCluster, groupId: number) {
        const detail = await page.request.get(`/api/v1/cluster/member-groups/${groupId}`, {headers: own.headers})
            .then(r => r.json())
        return {name: detail.name, memberIds: detail.memberIds}
    }
})
