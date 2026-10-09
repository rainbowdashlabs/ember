/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {APIRequestContext} from '@playwright/test'
import {expect, instanceRequestAs, peerBaseUrl, homeBaseUrl, stationManagerOf, test} from './fixtures/peer'
import {proveFreshly} from './fixtures/auth'
import {browserContextWith, demoSignIn} from './fixtures/session'
import {signInOneGo} from './fixtures/signing'
import {unique} from './fixtures/unique'

/**
 * A partner station's member signs the agreement of a shared appointment at their own station, on
 * their own instance: signing up hands them the organiser's document, the organiser lists them as
 * asked, and they sign it at home in one go.
 *
 * <p>The appointment lives on the second instance, at a station made for the story and paired with a
 * station of the first by an invite code, so the document really crosses from one installation to the
 * other. It names no member, which is what a document handed to partners has to do: one copy per date
 * that every partner's signer signs alike.
 *
 * <p>The sealed copy travelling back is not followed here. The organiser checks it against the
 * partner's signing authorities, which it asks the first instance for, and in this stack the second
 * instance cannot call the first: the address the first one publishes is the browser's. The check
 * and the copy kept are proved by the service tests of the partner agreements instead.
 */
test.describe('Signing for a partner\'s appointment', () => {
    test('a partner station\'s member is asked by the organiser and signs at home', async ({
        peerAdminApi,
        homeManagerApi,
        browser,
        request,
    }) => {
        const organiser = await stationManagerOf(peerBaseUrl())
        const created = await peerAdminApi.post('/api/v1/stations', {
            data: {name: unique('E2E-Unterschriftwache'), managerEmail: organiser.email},
        })
        expect(created.status(), await created.text()).toBe(201)
        const {id: owningStation} = await created.json()
        const owner = await instanceRequestAs(peerBaseUrl(), {email: organiser.email, stationId: owningStation})
        try {
            await proveFreshly(owner)
            const invited = await owner.post('/api/v1/federation/invite')
            expect(invited.ok(), await invited.text()).toBe(true)
            const {inviteCode} = await invited.json()
            await proveFreshly(homeManagerApi)
            const accepted = await homeManagerApi.post('/api/v1/federation/accept', {data: {inviteCode}})
            expect(accepted.status(), await accepted.text()).toBe(201)

            const {eventId, eventDate, agreement} = await sharedAppointmentAskingAgreement(owner)
            const signedUp = await homeManagerApi.post(`/api/v1/federated/${owningStation}/events/${eventId}/register`, {
                data: {eventDate, memberId: null},
            })
            expect(signedUp.status(), await signedUp.text()).toBe(201)
            await expect.poll(async () => signerStatesAt(owner, eventId, eventDate), {
                message: 'the organiser lists the partner\'s member as asked',
                timeout: 30_000,
            }).toContain('ASKED')

            const fields = await waitingAtHome(homeManagerApi, agreement)
            expect(fields.length, 'signing up hands the member the organiser\'s document at home').toBeGreaterThan(0)

            const home = await stationManagerOf(homeBaseUrl())
            const context = await browserContextWith(browser, await demoSignIn(request, home.email), home.stationId)
            const page = await context.newPage()
            try {
                await page.goto(`/station/signing?fields=${fields.join(',')}`)
                await signInOneGo(page)
            } finally {
                await context.close()
            }

            expect(await waitingAtHome(homeManagerApi, agreement), 'nothing of the agreement is left waiting at home')
                .toEqual([])
        } finally {
            await owner.dispose()
        }
    })
})

/**
 * An appointment of the organiser's station a week ahead, taking sign-ups, shared with every partner
 * and asking for a letter that names nobody, with one field for the participant.
 */
async function sharedAppointmentAskingAgreement(
    owner: APIRequestContext,
): Promise<{eventId: number; eventDate: string; agreement: string}> {
    const name = unique('Einverständnis')
    const template = await owner.post('/api/v1/document-templates', {data: {name}})
    expect(template.ok(), await template.text()).toBe(true)
    const templateId = (await template.json()).id as number
    const written = await owner.put(`/api/v1/document-templates/${templateId}`, {
        data: {
            name,
            kind: 'LETTER',
            forAppointments: true,
            body: [
                {sortOrder: 0, cells: [{sortOrder: 0, widthPercent: 100, contentType: 'MARKDOWN',
                    content: 'Ich nehme am Termin teil und halte mich an die Regeln.'}]},
                {sortOrder: 1, cells: [{sortOrder: 0, widthPercent: 50, contentType: 'SIGNATURE',
                    content: 'Teilnehmende Person', config: {signer: 'PARTICIPANT', statement: 'Ich bin dabei.'}}]},
            ],
        },
    })
    expect(written.ok(), `the organiser writes the agreement (${await written.text()})`).toBe(true)

    const start = new Date(Date.now() + 8 * 86_400_000)
    start.setUTCHours(10, 0, 0, 0)
    const created = await owner.post('/api/v1/events', {
        data: {
            name: unique('Geteilter Termin mit Unterschrift'),
            description: '',
            eventType: 'ONE_TIME',
            startTime: start.toISOString(),
            endTime: new Date(start.getTime() + 3_600_000).toISOString(),
            requiresRegistration: true,
            registrationDeadline: null,
        },
    })
    expect(created.status(), await created.text()).toBe(201)
    const eventId = (await created.json()).id as number
    const asked = await owner.put(`/api/v1/events/${eventId}/document-requirements`, {data: {templateIds: [templateId]}})
    expect(asked.ok(), `the appointment asks for the agreement (${await asked.text()})`).toBe(true)
    const shared = await owner.put(`/api/v1/events/${eventId}/federation`, {data: {scope: 'ALL_PARTNERS', partnerIds: []}})
    expect(shared.ok(), await shared.text()).toBe(true)
    return {eventId, eventDate: start.toISOString().slice(0, 10), agreement: name}
}

/**
 * The fields of the agreement still waiting at home, by id. Read by the document's name, since the
 * same manager signs up for the agreements of other stories running at the same time.
 */
async function waitingAtHome(home: APIRequestContext, agreement: string): Promise<number[]> {
    const open = await home.get('/api/v1/signing/open')
    expect(open.ok(), await open.text()).toBe(true)
    return (await open.json() as {fieldId: number; documentTitle: string | null}[])
        .filter(field => field.documentTitle?.startsWith(agreement))
        .map(field => field.fieldId)
}

/** Where every partner's signer stands with the agreement, as the organiser lists them for the date. */
async function signerStatesAt(owner: APIRequestContext, eventId: number, date: string): Promise<string[]> {
    const listed = await owner.get(`/api/v1/events/${eventId}/partner-agreements`, {params: {date}})
    expect(listed.ok(), await listed.text()).toBe(true)
    const signers = await listed.json() as {documents: {state: string}[]}[]
    return signers.flatMap(signer => signer.documents.map(document => document.state))
}
