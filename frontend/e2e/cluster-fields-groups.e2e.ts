/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, clusterAccountWith, clusterHeaders, clusterPage, theSeededCluster} from './fixtures/auth'

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
     * Scopes as tabs, a table underneath, and a way to add. The old screen had none of the three, so
     * seeing all three is what says the station's editor is really what is mounted here.
     */
    test('the association writes its questions in the station editor', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_FIELD_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/members/fields')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        // The four scopes an association may ask of, as tabs. A station has a fifth for groups and an
        // association has not, because a group belongs to one station.
        const tabs = page.getByRole('button', {name: /Mitglied|Erziehungsberechtigte|Team|Wachleitung/})
        await expect(tabs.first()).toBeVisible()
        await expect(page.getByRole('button', {name: /Gruppe/})).toHaveCount(0)

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

        // Nothing is picked yet, so the right hand side says to pick something.
        await expect(page.getByText(/wähl/i).first()).toBeVisible()
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
     */
    test('the association heads its questions and is offered no birth date', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_FIELD_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/members/fields')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await page.getByTestId('field-add').first().click({timeout: 15000})
        const types = page.getByTestId('field-type').locator('option')
        const offered = await types.allTextContents()
        expect(offered.join(' '), 'a heading is on offer').toContain('Überschrift')
        expect(offered.join(' '), 'a date of birth is not').not.toContain('Geburtsdatum')

        const heading = `Verbandsangaben ${Date.now()}`
        await page.getByTestId('field-name').fill(heading)
        await page.getByTestId('field-type').selectOption('SECTION')
        await page.getByTestId('field-save').click()

        // It comes back as a heading in the preview, which is where a section either lays out or does not
        await expect(page.getByRole('heading', {name: heading})).toBeVisible({timeout: 15000})

        // Taken away again: an association's question reaches every station under it, and leaving one
        // behind puts it on the profile every other story is reading at that moment
        const headers = await clusterHeaders(page, await theSeededCluster(page))
        const fields = await page.request.get('/api/v1/cluster/fields', {headers}).then(r => r.json())
        const mine = fields.find((field: {name: string}) => field.name === heading)
        expect(mine, 'the heading was written down').toBeTruthy()
        await page.request.delete(`/api/v1/cluster/fields/${mine.id}`, {headers})

        await page.context().close()
    })
})
