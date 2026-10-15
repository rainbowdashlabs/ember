/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect, apiHeaders, DEMO_PASSWORD} from './fixtures/auth'
import {ownMember, type OwnMember} from './fixtures/ownMember'
import {hydrated} from './fixtures/hydrated'
import {unique} from './fixtures/unique'

/**
 * A signature collected in person: the manager, who holds the right to collect signatures as part of
 * the document store, makes a photo consent on the spot for a trial user without a login, hands the
 * device over, the trial user reads, agrees and draws, hands it back, and the manager confirms with
 * their own password. Afterwards nothing of it waits for the trial user any more.
 */
async function consentTemplate(manager: Page): Promise<number> {
    const headers = await apiHeaders(manager)
    const name = unique('Fotoerlaubnis vor Ort')
    const created = await manager.request.post('/api/v1/document-templates', {headers, data: {name}})
    expect(created.ok(), `the manager makes a template (${await created.text()})`).toBe(true)
    const templateId = (await created.json()).id as number
    const written = await manager.request.put(`/api/v1/document-templates/${templateId}`, {
        headers,
        data: {
            name,
            kind: 'LETTER',
            body: [
                {sortOrder: 0, cells: [{sortOrder: 0, widthPercent: 100, contentType: 'MARKDOWN',
                    content: 'Fotos vom Übungsabend dürfen auf der Internetseite erscheinen.'}]},
                {sortOrder: 1, cells: [{sortOrder: 0, widthPercent: 50, contentType: 'SIGNATURE',
                    content: 'Teilnehmende Person', config: {signer: 'PARTICIPANT', statement: 'Ich bin einverstanden.'}}]},
            ],
        },
    })
    expect(written.ok(), `the manager writes the consent (${await written.text()})`).toBe(true)
    return templateId
}

async function trialUser(manager: Page): Promise<OwnMember> {
    const trial = await ownMember(manager, 'Probe')
    const changed = await manager.request.put(`/api/v1/station-members/${trial.memberId}/user-type`, {
        headers: await apiHeaders(manager),
        data: {userType: 'TRIAL'},
    })
    expect(changed.ok(), `the new member is a trial user (${await changed.text()})`).toBe(true)
    return trial
}

async function drawSignature(page: Page) {
    const box = await page.getByTestId('signature-canvas').boundingBox()
    if (!box) throw new Error('The drawing pad has no size')
    await page.mouse.move(box.x + box.width * 0.2, box.y + box.height * 0.6)
    await page.mouse.down()
    for (let step = 1; step <= 10; step++) {
        await page.mouse.move(box.x + box.width * (0.2 + step * 0.06), box.y + box.height * (step % 2 ? 0.35 : 0.65))
    }
    await page.mouse.up()
}

test.describe('Signatures collected in person', () => {
    test('a trial user signs a consent made on the spot in front of the manager', async ({managerPage}) => {
        const templateId = await consentTemplate(managerPage)
        const trial = await trialUser(managerPage)

        await managerPage.goto('/station/signing/witness')
        await hydrated(managerPage)
        await managerPage.getByTestId('witness-person-search').locator('input').fill(trial.surname)
        await managerPage.getByTestId(`witness-person-${trial.memberId}`).click()
        await managerPage.getByTestId(`witness-template-${templateId}`).click()

        const handedOver = managerPage.getByTestId('witness-handed-over-screen')
        await expect(handedOver, 'the device is handed over to the trial user').toBeVisible({timeout: 15_000})
        await handedOver.getByTestId('witness-handed-over').click()
        await handedOver.getByTestId('witness-agree').check()
        await handedOver.getByTestId('signing-next').click()
        await drawSignature(managerPage)
        await handedOver.getByTestId('signing-next').click()
        await handedOver.getByTestId('witness-taken-back').click()

        await expect(managerPage.getByTestId('witness-statement')).toContainText(trial.surname)
        const proof = managerPage.getByTestId('signing-proof')
        await proof.locator('input[type="password"]').fill(DEMO_PASSWORD)
        await proof.locator('form').filter({has: managerPage.locator('input[type="password"]')})
            .locator('button[type="submit"]').click()
        await expect(managerPage.getByTestId('witness-done')).toBeVisible({timeout: 30_000})

        const offers = await managerPage.request.get(`/api/v1/signing/witness/people/${trial.memberId}/offers`, {
            headers: await apiHeaders(managerPage),
        })
        expect(offers.ok(), await offers.text()).toBe(true)
        expect((await offers.json()).fields, 'nothing waits for the trial user any more').toEqual([])
    })
})
