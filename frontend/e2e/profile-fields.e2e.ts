/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders, pageAs} from './fixtures/auth'
import type {Page} from '@playwright/test'

/**
 * A station's questions, written once and put to whoever is meant to answer them.
 *
 * <p>The screen used to be a tab per kind of member, and a question belonged to the tab it was
 * written in. Asking two kinds the same thing meant writing it twice, and the two copies then
 * collected different answers to what reads as one question. Every story here walks the screen: that
 * one definition serves several forms is exactly the thing an API call cannot show.
 */
test.describe('Profile fields', () => {
    const FIELDS = '/station/members/config'

    async function openFields(page: Page) {
        await page.goto(FIELDS)
        await expect(page.getByTestId('app-shell')).toBeVisible()
    }

    /** Writes one question down and hands back its name and the row it landed in. */
    async function writeQuestion(page: Page, name: string) {
        await page.getByTestId('field-add').first().click({timeout: 15000})
        await page.getByTestId('field-name').fill(name)
        await page.getByTestId('field-type').selectOption('TEXT')
        await page.getByTestId('field-save').click()
        const row = page.getByTestId(`field-row-${name}`)
        await expect(row).toBeVisible({timeout: 15000})
        return row
    }

    /** Takes the question away again, so the stories that read a profile beside this one stay quiet. */
    async function removeQuestion(page: Page, name: string) {
        const headers = await apiHeaders(page)
        const fields = await page.request.get('/api/v1/profile-fields', {headers}).then(r => r.json())
        const mine = fields.find((field: {name: string}) => field.name === name)
        if (mine) await page.request.delete(`/api/v1/profile-fields/${mine.id}`, {headers})
    }

    /**
     * A question just written reaches nobody, and the row says so.
     *
     * <p>It used to be asked of whichever tab it was written in, which is a guess. A question that
     * reaches nobody looks exactly like one that works until somebody goes looking for it, so the
     * list says it out loud instead.
     */
    test('a new question is asked of nobody until somebody is named', async ({browser}) => {
        const page = await pageAs(browser, 'manager')
        await openFields(page)

        const name = `Spindnummer ${Date.now()}`
        const row = await writeQuestion(page, name)
        await expect(row.getByTestId('asked-of-nobody')).toBeVisible()

        await removeQuestion(page, name)
        await page.context().close()
    })

    /**
     * One definition, two audiences. The list still holds one row, which is the whole point: a
     * manager who is asked both forms meets the question once and answers it once.
     */
    test('one question is put to two kinds of member and stays one question', async ({browser}) => {
        const page = await pageAs(browser, 'manager')
        await openFields(page)

        const name = `Schuhgröße ${Date.now()}`
        const row = await writeQuestion(page, name)
        await row.click()

        const panel = page.getByTestId('audiences-panel')
        await panel.getByTestId('audience-add').selectOption('ROLE:MEMBER')
        await expect(panel.getByTestId('audience-Mitglieder')).toBeVisible({timeout: 15000})

        await panel.getByTestId('audience-add').selectOption('ROLE:TEAM')
        await expect(panel.getByTestId('audience-Team')).toBeVisible({timeout: 15000})

        await expect(page.getByTestId(`field-row-${name}`)).toHaveCount(1, {timeout: 15000})
        await expect(row.getByTestId('asked-of-nobody')).toHaveCount(0)

        await removeQuestion(page, name)
        await page.context().close()
    })

    /**
     * Dropping one audience leaves the question and every other audience alone, which is what tells a
     * definition apart from the form it happens to stand on.
     */
    test('dropping one audience keeps the question and the others', async ({browser}) => {
        const page = await pageAs(browser, 'manager')
        await openFields(page)

        const name = `Allergien ${Date.now()}`
        const row = await writeQuestion(page, name)
        await row.click()

        const panel = page.getByTestId('audiences-panel')
        await panel.getByTestId('audience-add').selectOption('ROLE:MEMBER')
        await expect(panel.getByTestId('audience-Mitglieder')).toBeVisible({timeout: 15000})
        await panel.getByTestId('audience-add').selectOption('ROLE:TEAM')
        await expect(panel.getByTestId('audience-Team')).toBeVisible({timeout: 15000})

        await panel.getByTestId('audience-Team').getByRole('button').last().click()
        await expect(panel.getByTestId('audience-Team')).toHaveCount(0, {timeout: 15000})
        await expect(panel.getByTestId('audience-Mitglieder')).toBeVisible()
        await expect(page.getByTestId(`field-row-${name}`)).toBeVisible()

        await removeQuestion(page, name)
        await page.context().close()
    })

    /**
     * The form below is the audience's own: a question put only to the team stands on the team's form
     * and on nobody else's.
     */
    test('each audience has a form of its own', async ({browser}) => {
        const page = await pageAs(browser, 'manager')
        await openFields(page)

        const name = `Funkrufname ${Date.now()}`
        const row = await writeQuestion(page, name)
        await row.click()

        const panel = page.getByTestId('audiences-panel')
        await panel.getByTestId('audience-add').selectOption('ROLE:TEAM')
        await expect(panel.getByTestId('audience-Team')).toBeVisible({timeout: 15000})

        await page.getByRole('button', {name: 'Team', exact: true}).click()
        await expect(page.getByTestId(`preview-tile-${name}`)).toBeVisible({timeout: 15000})

        await page.getByRole('button', {name: 'Erziehungsberechtigte', exact: true}).click()
        await expect(page.getByTestId(`preview-tile-${name}`)).toHaveCount(0, {timeout: 15000})

        await removeQuestion(page, name)
        await page.context().close()
    })
})
