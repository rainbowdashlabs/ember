/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect} from './fixtures/auth'
import {ownCluster, type OwnCluster} from './fixtures/cluster'

/**
 * An association filing its stations, and asking a question of some of them rather than all of them.
 *
 * Every story builds an association of its own. A question reaches every member station at once and a
 * filing decides which ones, so doing either to the seeded association would rearrange the profiles four
 * other workers are reading at that moment.
 *
 * The stations a filing is made of are made through the association's own station route rather than the
 * station fixture: what a group holds is a name in a list, and only the stories that read a profile need
 * anybody to be standing at one.
 */
test.describe('Cluster station groups', () => {
    /** One of the association's screens, entered the way the switcher enters it. */
    async function clusterScreen(page: Page, own: OwnCluster, path: string) {
        await page.goto(path)
        await page.evaluate(uid => window.localStorage.setItem('cluster_id', uid), own.uid)
        await page.goto(path)
        await expect(page.getByTestId('app-shell')).toBeVisible()
    }

    /** Another station under the association, with nobody at it. */
    async function stationCalled(page: Page, own: OwnCluster, name: string): Promise<string> {
        const made = await page.request.post('/api/v1/cluster/stations',
            {headers: own.headers, data: {name: `${own.name} ${name}`}})
        expect(made.ok(), `the association made a station (${await made.text()})`).toBeTruthy()
        return (await made.json()).uid
    }

    async function fileGroup(page: Page, own: OwnCluster, name: string): Promise<number> {
        const made = await page.request.post('/api/v1/cluster/station-groups',
            {headers: own.headers, data: {name}})
        expect(made.ok(), `the association filed a group (${await made.text()})`).toBeTruthy()
        return (await made.json()).id
    }

    async function putInGroup(page: Page, own: OwnCluster, groupId: number, stationUids: string[]) {
        const filed = await page.request.put(`/api/v1/cluster/station-groups/${groupId}/stations`,
            {headers: own.headers, data: {stationUids}})
        expect(filed.ok(), `the stations went into the group (${await filed.text()})`).toBeTruthy()
    }

    /** Asks a question, of one group or of every station. */
    async function ask(page: Page, own: OwnCluster, name: string, stationGroupId: number | null) {
        return page.request.post('/api/v1/cluster/fields', {
            headers: own.headers,
            data: {
                name, fieldType: 'BOOLEAN', config: {}, position: 0,
                scope: 'MEMBER', stationReadonly: false, keepOnArchive: false, stationGroupId,
            },
        })
    }

    /** Takes somebody on at one of the association's stations and returns the member id. */
    async function somebodyAt(page: Page, own: OwnCluster, stationUid: string, surname: string): Promise<number> {
        const taken = await page.request.post(
            `/api/v1/cluster/members/manage/stations/${stationUid}/members`,
            {headers: own.headers, data: {firstName: 'Erika', lastName: surname, email: `${surname.toLowerCase()}@e2e.ember`}})
        expect(taken.ok(), `the association took somebody on (${await taken.text()})`).toBeTruthy()
        return (await taken.json()).memberId
    }

    /** The questions one member is actually asked, by name. */
    async function questionsOf(page: Page, own: OwnCluster, memberId: number): Promise<string[]> {
        const response = await page.request.get(`/api/v1/cluster/members/manage/${memberId}/profile`,
            {headers: own.headers})
        expect(response.ok(), `the member's profile came back (${await response.text()})`).toBeTruthy()
        return ((await response.json()).fields ?? []).map((field: {name: string}) => field.name)
    }

    /**
     * CLS-90 - The association files its stations.
     *
     * A group is made on the screen, two of the three stations go into it, and the third stays on offer
     * rather than being listed as one of its own. Filing is a choice about some stations, and a screen
     * that cannot leave one out is not filing anything.
     */
    test('the association files two of its three stations into a group', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'Wachgruppen')
        await stationCalled(page, own, 'Nord')
        await stationCalled(page, own, 'Sued')

        await clusterScreen(page, own, '/cluster/stations/groups')

        await page.getByRole('button', {name: /Neue Gruppe/i}).click()
        const modal = page.getByTestId('modal')
        const name = `Atemschutz ${Date.now()}`
        await modal.getByPlaceholder(/Name der Gruppe/i).fill(name)
        await modal.getByRole('button', {name: 'Speichern', exact: true}).click()

        await page.getByText(name, {exact: true}).click()
        await expect(page.getByText(/Keine Wachen in dieser Gruppe/i)).toBeVisible()

        const offered = page.getByTestId('group-candidate')
        await expect(offered).toHaveCount(3)
        const spared = (await offered.nth(2).innerText()).trim()

        await offered.first().click()
        await expect(offered).toHaveCount(2)
        await offered.first().click()
        await expect(offered).toHaveCount(1)

        expect((await offered.innerText()).trim(),
            'the station left out is still on offer rather than filed').toBe(spared)

        await own.stationPage.context().close()
    })

    /**
     * CLS-91 - A question asked of one group reaches only that group.
     *
     * The whole point of the filing. Somebody at a station inside the group is asked; somebody at a
     * station outside it is not, and their profile is the same profile drawn by the same screen.
     */
    test('a question asked of one group reaches only the stations in it', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'NurDieGruppe')
        const outside = await stationCalled(page, own, 'Draussen')

        const group = await fileGroup(page, own, `Atemschutz ${Date.now()}`)
        await putInGroup(page, own, group, [own.stationUid])

        const question = `Atemschutztauglich${Date.now()}`
        const asked = await ask(page, own, question, group)
        expect(asked.ok(), `the question was asked of the group (${await asked.text()})`).toBeTruthy()

        const stamp = Date.now()
        const inGroup = await somebodyAt(page, own, own.stationUid, `Innen${stamp}`)
        const notInGroup = await somebodyAt(page, own, outside, `Aussen${stamp}`)

        expect(await questionsOf(page, own, inGroup),
            'somebody inside the filing is asked').toContain(question)
        expect(await questionsOf(page, own, notInGroup),
            'and somebody outside it is not').not.toContain(question)

        await clusterScreen(page, own, `/cluster/members/${inGroup}`)
        await expect(page.getByText(question)).toBeVisible({timeout: 15000})

        await page.goto(`/cluster/members/${notInGroup}`)
        await expect(page.getByRole('heading', {name: 'Angaben', exact: true})).toBeVisible({timeout: 15000})
        await expect(page.getByText(question)).toHaveCount(0)

        await own.stationPage.context().close()
    })

    /**
     * CLS-92 - The same question is never asked twice of one station.
     *
     * A question asked of everybody already reaches the stations in every group, so one of the same name
     * pointed at a group would put two boxes of one name on one profile. The refusal is the interesting
     * half of targeting: nothing in the database says these two collide until you work out what each
     * one reaches.
     */
    test('a second question of one name may not reach a station the first already does', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'NichtZweimal')
        const reaching = await fileGroup(page, own, `Erreicht ${Date.now()}`)
        const empty = await fileGroup(page, own, `Leer ${Date.now()}`)
        await putInGroup(page, own, reaching, [own.stationUid])

        const question = `Funkrufname${Date.now()}`
        expect((await ask(page, own, question, null)).ok(),
            'the question is asked of every station').toBeTruthy()

        const refused = await ask(page, own, question, reaching)
        expect(refused.status(), 'and the same name pointed at a group it already reaches is refused').toBe(400)
        expect(await refused.text()).toContain('already reaches')

        expect((await ask(page, own, question, empty)).ok(),
            'while a group holding nobody is nobody it could be asked twice').toBeTruthy()

        await own.stationPage.context().close()
    })

    /**
     * CLS-93 - A group with questions keyed to it cannot be deleted.
     *
     * Cascading would delete the questions and every answer to them without saying so. The refusal names
     * how many are in the way, and pointing them somewhere else lets the delete through.
     */
    test('a group questions are asked of cannot be removed until they are pointed elsewhere', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'NichtLoeschbar')
        const group = await fileGroup(page, own, `Atemschutz ${Date.now()}`)
        await putInGroup(page, own, group, [own.stationUid])

        const asked = await ask(page, own, `Atemschutztauglich${Date.now()}`, group)
        expect(asked.ok(), `the question was asked of the group (${await asked.text()})`).toBeTruthy()
        const fieldId = (await asked.json()).id

        const refused = await page.request.delete(`/api/v1/cluster/station-groups/${group}`,
            {headers: own.headers})
        expect(refused.status(), 'the filing is not removed out from under a question').toBe(400)
        expect(await refused.text()).toContain('1 question')

        const dropped = await page.request.delete(`/api/v1/cluster/fields/${fieldId}`, {headers: own.headers})
        expect(dropped.ok(), `the question was withdrawn (${await dropped.text()})`).toBeTruthy()

        const removed = await page.request.delete(`/api/v1/cluster/station-groups/${group}`,
            {headers: own.headers})
        expect(removed.ok(), `and now the filing goes (${await removed.text()})`).toBeTruthy()

        await clusterScreen(page, own, '/cluster/stations/groups')
        await expect(page.getByText(/Noch keine Gruppen/i)).toBeVisible()

        await own.stationPage.context().close()
    })

    /**
     * CLS-94 - Leaving a group hides the question and keeps the answer.
     *
     * A station taken out of a group stops being asked, and what somebody already answered is not thrown
     * away for it: put the station back and the answer is where it was. Anything else would make
     * rearranging the filing a destructive act.
     */
    test('a station taken out of a group stops being asked and keeps what it answered', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'KommtWieder')
        const group = await fileGroup(page, own, `Atemschutz ${Date.now()}`)
        await putInGroup(page, own, group, [own.stationUid])

        const question = `Atemschutztauglich${Date.now()}`
        const asked = await ask(page, own, question, group)
        expect(asked.ok(), `the question was asked of the group (${await asked.text()})`).toBeTruthy()
        const fieldId = (await asked.json()).id

        const memberId = await somebodyAt(page, own, own.stationUid, `Antwortet${Date.now()}`)
        const answered = await page.request.put(`/api/v1/cluster/fields/member/${memberId}`,
            {headers: own.headers, data: {values: {[fieldId]: 'true'}}})
        expect(answered.ok(), `the answer was written (${await answered.text()})`).toBeTruthy()

        await putInGroup(page, own, group, [])
        await clusterScreen(page, own, `/cluster/members/${memberId}`)
        await expect(page.getByRole('heading', {name: 'Angaben', exact: true})).toBeVisible({timeout: 15000})
        await expect(page.getByText(question)).toHaveCount(0)

        await putInGroup(page, own, group, [own.stationUid])
        await page.reload()
        await expect(page.getByText(question)).toBeVisible({timeout: 15000})
        const values = await page.request.get(`/api/v1/cluster/fields/member/${memberId}`,
            {headers: own.headers}).then(r => r.json())
        expect(values.values[fieldId], 'the answer waited out the time nobody was asking').toBe('true')

        await own.stationPage.context().close()
    })
})
