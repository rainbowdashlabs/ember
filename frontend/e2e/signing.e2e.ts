/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {APIRequestContext, Browser, Page} from '@playwright/test'
import {test, expect, apiHeaders, pageAsThrowaway, DEMO_PASSWORD} from './fixtures/auth'
import {cast} from './fixtures/cast'
import {ownMember, type OwnMember} from './fixtures/ownMember'
import {hydrated} from './fixtures/hydrated'

/**
 * Signing a document to bring, the way a guardian and a manager meet it: signing up a child ends on
 * the documents to sign, the reader signs everything in one go, a scan stands in for signing until a
 * manager settles it, a withdrawal asks again, a document added later is asked for, and a sealed copy
 * checks out on the verification page.
 *
 * <p>Every story makes its own appointment and its own child for the one guardian this spec was cast
 * as, who has no second factor and so confirms with the seeded password. The document is the demo's
 * photo consent, which asks the participant, a guardian and the issuer to sign; the issuer's field is
 * signed by the station when the copy is made.
 *
 * <p>Serial, because the guardian's charge is replaced by each story's own child.
 */
test.describe.configure({mode: 'serial'})

const PHOTO_CONSENT = 'Fotoerlaubnis Bürgerfest'
const DAY = 86_400_000

/** Where a story stands: its appointment on one date, the child signed up for it and both pages. */
interface Scene {
    eventId: number
    date: string
    child: OwnMember
    guardian: Page
}

interface RequirementState {
    signature: {state: string; fields: {state: string; yours: boolean}[]} | null
    paper: {state: string} | null
    documentId: number | null
}

async function photoConsentTemplate(manager: Page): Promise<number> {
    const listed = await manager.request.get('/api/v1/document-requirements/templates', {
        headers: await apiHeaders(manager),
        params: {q: PHOTO_CONSENT, size: '5'},
    })
    expect(listed.ok(), `the templates appointments may ask for are listed (${await listed.text()})`).toBeTruthy()
    const found = (await listed.json() as {items: {id: number; name: string}[]}).items
        .find(item => item.name === PHOTO_CONSENT)
    if (!found) throw new Error(`The demo seeds no template named ${PHOTO_CONSENT}`)
    return found.id
}

/** An appointment of the story's own a week ahead, asking for sign-ups and, unless told not to, the photo consent. */
async function appointment(manager: Page, withConsent = true): Promise<{eventId: number; date: string}> {
    const headers = await apiHeaders(manager)
    const start = new Date(Date.now() + 8 * DAY)
    start.setUTCHours(10, 0, 0, 0)
    const created = await manager.request.post('/api/v1/events', {
        headers,
        data: {
            name: `Unterschrift ${test.info().workerIndex}-${Date.now()}`,
            description: 'Probe für Unterschriften',
            eventType: 'ONE_TIME',
            startTime: start.toISOString(),
            endTime: new Date(start.getTime() + 3_600_000).toISOString(),
            requiresRegistration: true,
            registrationDeadline: new Date(Date.now() + 3 * DAY).toISOString(),
        },
    })
    expect(created.ok(), `the organiser made an appointment (${await created.text()})`).toBeTruthy()
    const eventId = (await created.json()).id as number
    if (withConsent) await askFor(manager, eventId)
    return {eventId, date: start.toISOString().slice(0, 10)}
}

async function askFor(manager: Page, eventId: number) {
    const asked = await manager.request.put(`/api/v1/events/${eventId}/document-requirements`, {
        headers: await apiHeaders(manager),
        data: {templateIds: [await photoConsentTemplate(manager)]},
    })
    expect(asked.ok(), `the appointment asks for the photo consent (${await asked.text()})`).toBeTruthy()
}

/** A child of the story's own, in the care of the cast guardian and of nobody else they look after. */
async function child(manager: Page): Promise<OwnMember> {
    const charge = await ownMember(manager, 'Kind')
    const guardian = (await cast()).guardians.signingSpec
    const attached = await manager.request.put(`/api/v1/station-members/${guardian.memberId}/managed`, {
        headers: await apiHeaders(manager),
        data: {managedIds: [charge.memberId]},
    })
    expect(attached.ok(), `the guardian is given the child (${await attached.text()})`).toBeTruthy()
    return charge
}

async function guardianPage(browser: Browser, request: APIRequestContext): Promise<Page> {
    return pageAsThrowaway(browser, request, [], (await cast()).guardians.signingSpec)
}

async function scene(browser: Browser, request: APIRequestContext, manager: Page, withConsent = true): Promise<Scene> {
    const {eventId, date} = await appointment(manager, withConsent)
    return {eventId, date, child: await child(manager), guardian: await guardianPage(browser, request)}
}

/** The guardian signs the child up, and only the child, ending on the documents to sign when there are any. */
async function signUpChild(scene: Scene) {
    const page = scene.guardian
    const guardianMember = (await cast()).guardians.signingSpec.memberId
    await page.goto(`/station/events/${scene.eventId}`)
    await hydrated(page)
    await page.getByRole('tab', {name: 'Anmeldungen'}).click()
    await page.getByTestId('answer-household').click()
    const own = page.getByTestId(`answer-for-${guardianMember}`)
    if (await own.count() > 0 && await own.isChecked()) await own.uncheck()
    await page.getByTestId('answer-confirm').click()
}

/** Where the child stands with the photo consent, as whoever runs the appointment sees it. */
async function requirementOf(manager: Page, scene: Scene): Promise<RequirementState | undefined> {
    const answered = await manager.request.get(`/api/v1/events/${scene.eventId}/documents-to-bring`, {
        headers: await apiHeaders(manager),
        params: {date: scene.date},
    })
    expect(answered.ok(), `the manager reads the documents to bring (${await answered.text()})`).toBeTruthy()
    const documents = await answered.json() as {participants: {memberId: number; documents: RequirementState[]}[] | null}
    return documents.participants?.find(row => row.memberId === scene.child.memberId)?.documents[0]
}

/** The fields waiting for the guardian, read from their open tasks. */
async function openFields(guardian: Page): Promise<{fieldId: number; documentTitle: string; memberName: string}[]> {
    const open = await guardian.request.get('/api/v1/signing/open', {headers: await apiHeaders(guardian)})
    expect(open.ok(), `the guardian reads their open signatures (${await open.text()})`).toBeTruthy()
    return open.json()
}

/** A few strokes on the drawing pad, which is all a signature picture needs. */
async function drawSignature(page: Page) {
    const pad = page.getByTestId('signature-canvas')
    const box = await pad.boundingBox()
    if (!box) throw new Error('The drawing pad has no size')
    await page.mouse.move(box.x + box.width * 0.2, box.y + box.height * 0.6)
    await page.mouse.down()
    for (let step = 1; step <= 10; step++) {
        await page.mouse.move(box.x + box.width * (0.2 + step * 0.06), box.y + box.height * (step % 2 ? 0.35 : 0.65))
    }
    await page.mouse.up()
}

/**
 * Walks the signing screens from the overview to the end: every field ticked, every detail typed,
 * a picture drawn wherever none is kept, the check, and the password as the one confirmation.
 */
async function signInOneGo(page: Page) {
    await expect(page.getByTestId('signing-overview')).toBeVisible({timeout: 15_000})
    await page.getByTestId('signing-start').click()
    const proof = page.getByTestId('signing-proof')
    const next = page.getByTestId('signing-next')
    for (let screen = 0; screen < 12; screen++) {
        await expect(next.or(proof).first()).toBeVisible({timeout: 15_000})
        if (await proof.isVisible()) break
        for (const box of await page.locator('[data-testid^="signing-agree-"]').all()) {
            if (!(await box.isChecked())) await box.check()
        }
        for (const entry of await page.locator('[data-testid^="signing-fill-in-"]').all()) {
            if (!(await entry.inputValue())) await entry.fill('0171 2345678')
        }
        if (await page.getByTestId('signature-canvas').isVisible()) await drawSignature(page)
        await next.click()
    }
    await proof.locator('input[type="password"]').fill(DEMO_PASSWORD)
    await proof.locator('form').filter({has: page.locator('input[type="password"]')})
        .locator('button[type="submit"]').click()
    await expect(page.getByTestId('signing-done')).toBeVisible({timeout: 30_000})
}

async function signedUpAndSigned(scene: Scene) {
    await signUpChild(scene)
    const step = scene.guardian.getByTestId('registration-signing-step')
    await expect(step, 'signing up ends on the documents to sign').toBeVisible({timeout: 15_000})
    await step.getByTestId('registration-signing-now').click()
    await signInOneGo(scene.guardian)
}

async function cleanUp(manager: Page, scene: Scene) {
    await manager.request.delete(`/api/v1/events/${scene.eventId}`, {headers: await apiHeaders(manager)})
    await scene.guardian.context().close()
}

test.describe('Signing documents to bring', () => {
    test('a guardian signs up a child and signs everything for it in one go', async ({browser, request, managerPage}) => {
        const story = await scene(browser, request, managerPage)

        await signedUpAndSigned(story)

        await expect.poll(async () => (await requirementOf(managerPage, story))?.signature?.state, {
            message: 'the manager sees the photo consent signed',
            timeout: 30_000,
        }).toBe('SIGNED')
        expect(await openFields(story.guardian), 'nothing is left waiting for the guardian')
            .toEqual(expect.not.arrayContaining([expect.objectContaining({memberName: expect.stringContaining(story.child.surname)})]))
        await cleanUp(managerPage, story)
    })

    test('a scan handed in stands in for signing until a manager confirms it', async ({browser, request, managerPage}) => {
        const story = await scene(browser, request, managerPage)
        await signUpChild(story)
        const step = story.guardian.getByTestId('registration-signing-step')
        await expect(step).toBeVisible({timeout: 15_000})

        await step.locator('input[type="file"]').setInputFiles({
            name: 'fotoerlaubnis.pdf',
            mimeType: 'application/pdf',
            buffer: Buffer.from('%PDF-1.4\n% unterschriebener Scan\n%%EOF\n', 'latin1'),
        })
        await expect(step.getByTestId('document-scan-received'), 'the guardian is told the scan arrived')
            .toBeVisible({timeout: 15_000})
        await step.getByTestId('registration-signing-later').click()

        expect(await openFields(story.guardian), 'nobody is asked to sign while the scan waits')
            .toEqual(expect.not.arrayContaining([expect.objectContaining({memberName: expect.stringContaining(story.child.surname)})]))

        await managerPage.goto(`/station/events/${story.eventId}`)
        await hydrated(managerPage)
        const confirm = managerPage.getByTestId('document-scan-confirm').first()
        await expect(confirm, 'the manager is offered the scan to confirm').toBeVisible({timeout: 15_000})
        await confirm.click()

        await expect.poll(async () => (await requirementOf(managerPage, story))?.signature?.state, {
            message: 'the confirmed scan counts as signed on paper',
            timeout: 30_000,
        }).toBe('PAPER_CONFIRMED')
        await cleanUp(managerPage, story)
    })

    test('a withdrawn signature asks for the agreement again', async ({browser, request, managerPage}) => {
        const story = await scene(browser, request, managerPage)
        await signedUpAndSigned(story)
        await expect.poll(async () => (await requirementOf(managerPage, story))?.signature?.state, {timeout: 30_000})
            .toBe('SIGNED')

        await story.guardian.goto(`/station/events/${story.eventId}`)
        await hydrated(story.guardian)
        await story.guardian.getByTestId('agreement-withdraw').first().click()
        const form = story.guardian.getByTestId('agreement-withdraw-form')
        await expect(form).toBeVisible()
        await form.getByRole('button', {name: /widerrufen/i}).click()

        await expect.poll(async () => (await openFields(story.guardian))
            .filter(field => field.memberName.includes(story.child.surname)).length, {
            message: 'the agreement is asked for again',
            timeout: 30_000,
        }).toBeGreaterThan(0)
        await cleanUp(managerPage, story)
    })

    test('a document added after signing up is asked for', async ({browser, request, managerPage}) => {
        const story = await scene(browser, request, managerPage, false)
        await signUpChild(story)
        await expect(story.guardian.getByTestId(`my-answer-${story.child.memberId}`)).toHaveText(/Bestätigt|Ausstehend/,
            {timeout: 15_000})
        expect(await openFields(story.guardian), 'nothing is asked while the appointment asks for nothing')
            .toEqual(expect.not.arrayContaining([expect.objectContaining({memberName: expect.stringContaining(story.child.surname)})]))

        await askFor(managerPage, story.eventId)

        await expect.poll(async () => (await openFields(story.guardian))
            .filter(field => field.memberName.includes(story.child.surname) && field.documentTitle.includes('Fotoerlaubnis'))
            .length, {
            message: 'the document added later reaches the guardian',
            timeout: 30_000,
        }).toBeGreaterThan(0)
        await cleanUp(managerPage, story)
    })

    test('a signed copy checks out as sealed by this installation', async ({browser, request, managerPage, page}) => {
        const story = await scene(browser, request, managerPage)
        await signedUpAndSigned(story)
        await expect.poll(async () => (await requirementOf(managerPage, story))?.signature?.state, {timeout: 30_000})
            .toBe('SIGNED')

        const documentId = (await requirementOf(managerPage, story))?.documentId
        if (!documentId) throw new Error('The signed copy is filed nowhere')
        const content = await managerPage.request.get(`/api/v1/documents/${documentId}/content`, {
            headers: await apiHeaders(managerPage),
        })
        expect(content.ok(), `the manager downloads the signed copy (${content.status()})`).toBeTruthy()

        await page.goto('/verify')
        await hydrated(page)
        await page.locator('input[type="file"]').setInputFiles({
            name: 'fotoerlaubnis.pdf',
            mimeType: 'application/pdf',
            buffer: await content.body(),
        })
        await expect(page.getByTestId('seal-check').first()).toHaveAttribute('data-verdict', 'sealedHere',
            {timeout: 30_000})
        await expect(page.getByTestId('seal-evidence'), 'the page names who signed').toBeVisible()
        await cleanUp(managerPage, story)
    })
})
