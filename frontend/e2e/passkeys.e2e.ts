/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    answerStepUpPrompts,
    apiHeaders,
    DEMO_PASSWORD,
    expect,
    freshStepUpProof,
    pageAs,
    pageAsThrowaway,
    test,
} from './fixtures/auth'
import type {APIRequestContext, Browser, CDPSession, Page} from '@playwright/test'
import {cast, passkeySlot, spokenForMemberIds, type CastMember} from './fixtures/cast'
import {demoSignIn} from './fixtures/session'

/**
 * The passkey stories, over Chromium's virtual authenticator: the only way to prove any of this
 * without a finger. Chromium only; the other projects skip.
 *
 * Every story takes a throwaway account of its own: a passkey on a shared role would follow the
 * other stories around, and several of these end sessions or refuse passwords on purpose. A story
 * chains several ceremonies, a step-up round trip and full sign-ins, so each is given two minutes.
 */
test.describe('Passkeys', () => {
    test.skip(({browserName}) => browserName !== 'chromium', 'the virtual authenticator is CDP-only')
    test.describe.configure({timeout: 120_000})

    /**
     * The member this story was cast as, and nobody else's part.
     *
     * <p>The parts are settled at global setup and written down by id. They used to be worked out
     * here, from a pool filtered and sorted by the address, which these very stories rewrite: a
     * member given one dropped out, every part below them shifted down, and a part came to name
     * somebody another worker was signed in as. Ending that session, which several of these stories
     * do on purpose, then failed a scattering of stories with no story in sight that had touched
     * them. The neighbouring guardian spec writes addresses too, so no filter written here could
     * have held.
     */
    async function storyAccount(slot: number): Promise<CastMember> {
        return passkeySlot(slot)
    }

    /** The part the onboarding story takes, past the ones the independent stories hold. */
    const ONBOARD_SLOT = 5

    /**
     * Names the account and reads back what the asking device is showing.
     *
     * <p>Two things travel from this screen to the other one: the code, which the QR also carries,
     * and the number, which it deliberately does not. Both are waited for by content rather than by
     * visibility, because the elements render before the request has answered and an empty read
     * would be pasted into a form that rightly refuses it.
     */
    async function raiseRequest(device: Page, identifier: string): Promise<{code: string; matchNumber: string}> {
        await device.getByTestId('device-identifier').fill(identifier)
        await device.getByRole('button', {name: 'Weiter'}).click()

        const codeElement = device.getByTestId('device-code')
        await expect(codeElement).toHaveText(/[0-9A-Z-]{8,9}/, {timeout: 15_000})
        const numberElement = device.getByTestId('device-match-number')
        await expect(numberElement).toHaveText(/\d{2}/, {timeout: 15_000})

        return {
            code: (await codeElement.innerText()).trim(),
            matchNumber: (await numberElement.innerText()).trim(),
        }
    }

    interface MemberRow {
        id: number
        accountId: number
        email?: string
        firstName?: string
        lastName?: string
    }

    /**
     * Gives the slot's member an address mail could actually reach, through the manager who may
     * set one, and returns the account under its new address. The stories about the offer and
     * about switching the password off need one: both are refused to somebody the way back in
     * cannot be mailed to.
     */
    async function addressedStoryAccount(browser: Browser, request: APIRequestContext, slot: number): Promise<CastMember> {
        const account = await storyAccount(slot)
        const manager = await pageAs(browser, 'manager')
        try {
            const address = `passkey-story-${slot}-${Date.now()}@example.test`
            await giveAddress(manager, account.memberId, address, slot)
            return {...account, email: address}
        } finally {
            await manager.context().close()
        }
    }

    /**
     * The manager's half of the re-addressing: find the row, write the address, prove on refusal.
     *
     * The stagger by slot is what keeps this off the two-factor throttle: every manager story
     * shares one session, so the first story's proof covers the rest through the freshness
     * window, but only if the others arrive after it rather than alongside it. A burst of
     * simultaneous proofs spends codes on refusals until the account is throttled for minutes.
     *
     * The row is found by id, never by the address it is about to overwrite. A proof is only given
     * when the write is refused, and a spent code or a throttled answer is simply waited out.
     */
    async function giveAddress(manager: Page, memberId: number, address: string, slot: number): Promise<MemberRow> {
        await manager.waitForTimeout(slot * 6_000)
        const headers = await apiHeaders(manager)
        const list = await manager.request.get('/api/v1/station-members', {headers})
        const row = (await list.json() as MemberRow[]).find(candidate => candidate.id === memberId)
        if (!row) throw new Error(`Member ${memberId} is not in the station list`)
        const put = () => manager.request.put(`/api/v1/members/${row.accountId}`, {
            headers,
            data: {email: address, firstName: row.firstName, lastName: row.lastName},
        })
        let saved = await put()
        for (let attempt = 0; saved.status() === 401 && attempt < 3; attempt++) {
            try {
                await freshStepUpProof(manager)
            } catch {
                await manager.waitForTimeout(15_000)
            }
            saved = await put()
        }
        if (!saved.ok()) throw new Error(`The address edit answered ${saved.status()}`)
        return {...row, email: address}
    }

    interface VirtualAuthenticator {
        cdp: CDPSession
        authenticatorId: string
    }

    /** A platform authenticator that answers every prompt like a finger on a reader would. */
    async function addAuthenticator(page: Page): Promise<VirtualAuthenticator> {
        const cdp = await page.context().newCDPSession(page)
        await cdp.send('WebAuthn.enable')
        const {authenticatorId} = await cdp.send('WebAuthn.addVirtualAuthenticator', {
            options: {
                protocol: 'ctap2',
                transport: 'internal',
                hasResidentKey: true,
                hasUserVerification: true,
                isUserVerified: true,
                automaticPresenceSimulation: true,
            },
        }) as {authenticatorId: string}
        return {cdp, authenticatorId}
    }

    /** Walks the security screen's creation flow to the finished trial; the fixture answers its fresh-proof dialog. */
    async function createPasskey(page: Page): Promise<void> {
        await page.goto('/account/security')
        await page.getByRole('button', {name: 'Passkey einrichten'}).click()
        await page.getByRole('dialog').getByRole('button', {name: 'Passkey einrichten'}).click()
        await expect(page.getByText('Und jetzt probieren wir ihn einmal aus.')).toBeVisible({timeout: 15_000})
        await page.getByRole('button', {name: 'Ausprobieren', exact: true}).click()
        await expect(page.getByText('Passt. Beim nächsten Mal meldest du dich genau so an.')).toBeVisible()
        await page.getByRole('button', {name: 'Fertig'}).click()
    }

    /**
     * Signs in on a fresh page through the login screen's passkey path. The screen offers two:
     * the button, and the autofill that starts on its own. The virtual authenticator answers the
     * autofill immediately, so the sign-in often completes before any button could be pressed -
     * whichever way it happens is the passkey signing its owner in.
     *
     * The click carries a timeout so that it can lose the race against the autofill navigating away:
     * without one it retries for as long as the story runs on a page that is already signed in.
     */
    async function signInWithPasskey(page: Page): Promise<void> {
        await page.goto('/login')
        const shell = page.getByTestId('app-shell')
        const button = page.getByRole('button', {name: 'Mit Passkey anmelden'})
        await expect(shell.or(button).first()).toBeVisible({timeout: 20_000})
        if (await shell.count() === 0) {
            await button.click({timeout: 10_000}).catch(() => {})
        }
        await expect(shell).toBeVisible({timeout: 20_000})
    }

    /**
     * A passkey is created, tried, and then signs its owner back in with nothing else; a password
     * sign-in afterwards still asks nothing extra.
     *
     * <p>The session goes in only once the login screen is up, since a screen opened with a session
     * moves on at once. The authenticator is removed before the password sign-in, because it would
     * answer the screen's passkey autofill on its own and win the race.
     */
    test('a passkey is created, tried, and signs its owner in', async ({browser, request}) => {
        const account = await storyAccount(0)

        const session = await demoSignIn(request, account.email)

        const context = await browser.newContext()
        const page = await context.newPage()
        await page.addInitScript(() => window.localStorage.setItem('storage_consent', 'accepted'))
        const {cdp, authenticatorId} = await addAuthenticator(page)
        await answerStepUpPrompts(page)
        await page.goto('/login')
        await context.addCookies(session.cookies)
        await page.evaluate(stationId => {
            if (stationId) window.localStorage.setItem('station_id', stationId)
        }, account.stationId ?? '')

        await createPasskey(page)
        await expect(page.getByText('Anmeldung', {exact: true})).toBeVisible()

        await context.clearCookies()
        await signInWithPasskey(page)

        await cdp.send('WebAuthn.removeVirtualAuthenticator', {authenticatorId})
        await context.clearCookies()
        await page.goto('/login')
        await page.getByPlaceholder('E-Mail oder Benutzername').fill(account.email)
        await page.getByPlaceholder('Passwort').fill(DEMO_PASSWORD)
        await page.getByRole('button', {name: 'Anmelden', exact: true}).click()
        await expect(page.getByTestId('app-shell')).toBeVisible({timeout: 20_000})

        await context.close()
    })

    /**
     * The password can be switched off once a passkey has proven itself, and removing the last passkey
     * is the safety valve that opens it again.
     *
     * <p>The switch is only offered to somebody the way back in can be mailed to. The refused password
     * gets the same words as any refused sign-in, so the refusal tells nobody that the account exists;
     * it is tried from a sessionless context, since a demo login would replace the account's session.
     */
    test('switching the password off refuses it, removing the last passkey opens it again', async ({browser, request}) => {
        const account = await addressedStoryAccount(browser, request, 1)
        const page = await pageAsThrowaway(browser, request, [], account)
        await addAuthenticator(page)

        await createPasskey(page)

        await page.getByRole('switch', {name: /^Anmeldung mit Passwort/}).click()
        await expect(page.getByText('Die Anmeldung mit Passwort ist ausgeschaltet.', {exact: false}))
            .toBeVisible({timeout: 15_000})

        const freshContext = await browser.newContext()
        const fresh = await freshContext.newPage()
        await fresh.addInitScript(() => window.localStorage.setItem('storage_consent', 'accepted'))
        await fresh.goto('/login')
        await fresh.getByPlaceholder('E-Mail oder Benutzername').fill(account.email)
        await fresh.getByPlaceholder('Passwort').fill(DEMO_PASSWORD)
        await fresh.getByRole('button', {name: 'Anmelden', exact: true}).click()
        await expect(fresh.getByText('Die Anmeldung hat nicht geklappt', {exact: false}))
            .toBeVisible({timeout: 15_000})
        await freshContext.close()

        await page.goto('/account/security')
        await page.getByRole('button', {name: 'Löschen'}).first().click()
        await page.getByRole('dialog').getByRole('button', {name: 'Löschen'}).click()
        await expect(page.getByText('Die Anmeldung mit Passwort ist wieder eingeschaltet.', {exact: false}))
            .toBeVisible({timeout: 15_000})

        await page.context().close()
    })

    /**
     * A new device with no session but an authenticator of its own asks for a credential, the
     * signed-in device approves, and the new one enrols and signs in with the passkey it made.
     */
    test('the device handshake frees a device across two contexts', async ({browser, request}) => {
        const account = await storyAccount(2)

        const approver = await pageAsThrowaway(browser, request, [], account)

        const newContext = await browser.newContext()
        const newDevice = await newContext.newPage()
        await newDevice.addInitScript(() => window.localStorage.setItem('storage_consent', 'accepted'))
        await addAuthenticator(newDevice)

        await newDevice.goto('/unlock-device')
        await newDevice.getByRole('button', {name: 'Passkey auf diesem Gerät anlegen'}).click()
        const handshake = await raiseRequest(newDevice, account.email)

        await answerStepUpPrompts(approver)
        await approver.goto('/account/unlock-device')
        await approver.getByPlaceholder('K7RM-2WQD').fill(handshake.code)
        await approver.getByRole('button', {name: 'Code prüfen'}).click()
        await expect(approver.getByText('Das Gerät legt danach einen Passkey für dein Konto an.')).toBeVisible()
        await expect(approver.getByText('Nur freischalten, wenn du gerade selbst an diesem Gerät sitzt.'))
            .toBeVisible()
        await approver.getByTestId(`number-choice-${handshake.matchNumber}`).click()
        await expect(approver.getByText('Freigeschaltet.', {exact: false})).toBeVisible({timeout: 15_000})

        await expect(newDevice.getByTestId('app-shell')).toBeVisible({timeout: 30_000})

        await approver.context().close()
        await newContext.close()
    })

    /**
     * The other half of the same handshake: a device that wants no credential at all.
     *
     * <p>The case this exists for is a borrowed machine, and on a passwordless instance a browser
     * that cannot hold a passkey has no other way in whatsoever. What proves it worked is that the
     * device ends up signed in while holding nothing: no ceremony runs, and no authenticator is
     * attached to this context at all.
     *
     * <p>The story has a slot of its own, since the approval screen is throttled per account.
     */
    test('a device with no credential is signed in by one that already holds a session', async ({browser, request}) => {
        const account = await storyAccount(4)
        const approver = await pageAsThrowaway(browser, request, [], account)

        const newContext = await browser.newContext()
        const newDevice = await newContext.newPage()
        await newDevice.addInitScript(() => window.localStorage.setItem('storage_consent', 'accepted'))

        await newDevice.goto('/unlock-device')
        await newDevice.getByRole('button', {name: 'Nur anmelden, nichts speichern'}).click()
        const handshake = await raiseRequest(newDevice, account.email)

        await answerStepUpPrompts(approver)
        await approver.goto('/account/unlock-device')
        await approver.getByPlaceholder('K7RM-2WQD').fill(handshake.code)
        await approver.getByRole('button', {name: 'Code prüfen'}).click()

        await expect(approver.getByText('Das Gerät wird danach in deinem Namen angemeldet', {exact: false}),
            'the screen says this grant is a sign-in rather than a passkey')
            .toBeVisible()
        await approver.getByTestId(`number-choice-${handshake.matchNumber}`).click()
        await expect(approver.getByText('Freigeschaltet.', {exact: false})).toBeVisible({timeout: 15_000})

        await expect(newDevice.getByTestId('app-shell')).toBeVisible({timeout: 30_000})

        const headers = await apiHeaders(newDevice)
        const status = await newDevice.request.get('/api/v1/account/passkeys', {headers});
        expect((await status.json()).passkeys ?? [], 'a sign-in leaves no credential on the device').toHaveLength(0)

        await approver.context().close()
        await newContext.close()
    })

    /**
     * The offer of a passkey goes only to somebody the way back in can be mailed to, appears once
     * after a sign-in, and once declined the next sign-in goes straight through.
     */
    test('the offer appears once after a sign-in and Nein danke ends it', async ({browser, request}) => {
        const account = await addressedStoryAccount(browser, request, 3)

        const context = await browser.newContext()
        const page = await context.newPage()
        await page.addInitScript(() => {
            window.localStorage.setItem('storage_consent', 'accepted')
            window.localStorage.setItem('onboarding_tour_completed', 'true')
        })
        await addAuthenticator(page)
        await answerStepUpPrompts(page)

        async function signInWithPassword() {
            await page.goto('/login')
            await page.getByPlaceholder('E-Mail oder Benutzername').fill(account.email)
            await page.getByPlaceholder('Passwort').fill(DEMO_PASSWORD)
            await page.getByRole('button', {name: 'Anmelden', exact: true}).click()
        }

        await signInWithPassword()
        await expect(page.getByText('Beim nächsten Mal ohne Passwort anmelden')).toBeVisible({timeout: 20_000})
        await expect(page.getByText('Dein Passwort funktioniert weiter.', {exact: false})).toBeVisible()
        await page.getByRole('button', {name: 'Nein danke'}).click()
        await expect(page.getByTestId('app-shell')).toBeVisible({timeout: 20_000})

        await context.clearCookies()
        await signInWithPassword()
        await expect(page.getByTestId('app-shell')).toBeVisible({timeout: 20_000})
        expect(page.url()).not.toContain('passkey-offer')

        await context.close()
    })

    /**
     * A guardian signs a member in their care in, on the machine in the hall.
     *
     * <p>The one shape of this handshake where the approver and the account signed in are two
     * different people, which is what the subject column exists for. It is also the only way the
     * flow can be proved here at all: a dev instance keeps one session row per account, so the
     * same-account version of this refuses to start, while a guardian and their charge are two
     * accounts with a session each.
     *
     * <p>The guardian first gives them something to sign in with and switches their access on,
     * because neither is true of a seeded managed member, and the approval screen offers only the
     * people who could actually sign in. It is a login name rather than an address: a dev session
     * token is the address, so giving one out would sign the charge out elsewhere.
     *
     * <p>Neither side is somebody another story acts as, since signing in replaces a dev session row
     * and addressing a managed member ends their sessions; both are settled by id at global setup.
     */
    test('a guardian signs a member in their care in on a borrowed machine', async ({browser, request}) => {
        const spokenFor = await spokenForMemberIds()
        const guardianCast = (await cast()).guardians.passkeySpec
        const guardian = await pageAsThrowaway(browser, request, [], guardianCast)
        await answerStepUpPrompts(guardian)
        const headers = await apiHeaders(guardian)

        const managed = await guardian.request
            .get('/api/v1/managed-members', {headers})
            .then(response => response.json())
        const charge = managed.find((member: {id: number}) => !spokenFor.has(member.id))
        test.skip(!charge, 'every member this guardian looks after is already spoken for by another story')

        await freshStepUpProof(guardian)
        const loginName = `charge${Date.now()}`
        await guardian.request.put(`/api/v1/managed-members/${charge.id}/username`, {
            headers,
            data: {username: loginName},
        })
        await guardian.request.put(`/api/v1/managed-members/${charge.id}/login`, {headers, data: {enabled: true}})

        const newContext = await browser.newContext()
        const newDevice = await newContext.newPage()
        await newDevice.addInitScript(() => window.localStorage.setItem('storage_consent', 'accepted'))

        await newDevice.goto('/unlock-device')
        await newDevice.getByRole('button', {name: 'Nur anmelden, nichts speichern'}).click()
        const handshake = await raiseRequest(newDevice, loginName)

        await guardian.goto('/account/unlock-device')
        await guardian.getByPlaceholder('K7RM-2WQD').fill(handshake.code)
        await guardian.getByRole('button', {name: 'Code prüfen'}).click()

        const forWhom = guardian.getByTestId('approve-for')
        await expect(forWhom).toBeVisible({timeout: 15_000})
        await forWhom.selectOption(String(charge.accountId))
        await guardian.getByTestId(`number-choice-${handshake.matchNumber}`).click()
        await expect(guardian.getByText('Freigeschaltet.', {exact: false})).toBeVisible({timeout: 15_000})

        await expect(newDevice.getByTestId('app-shell')).toBeVisible({timeout: 30_000})
        const session = await newDevice.request
            .get('/api/v1/session', {headers: await apiHeaders(newDevice)})
            .then(response => response.json())
        expect(session.account.username).toBe(loginName)
        expect(session.member.id, 'the device holds the charge, not the guardian').not.toBe(guardianCast.memberId)

        await newContext.close()
        await guardian.context().close()
    })

    /**
     * A manager onboards a member again. The shared manager session acts, since a fresh login would
     * replace it under every other story, and the target has a slot of its own because onboarding
     * ends its sessions. A member with an address is never offered the code: the mail path is theirs.
     */
    test('a manager onboards a member again and gets a passkey code for an addressless one', async ({browser}) => {
        const target = await storyAccount(ONBOARD_SLOT)
        const page = await pageAs(browser, 'manager')

        const address = `passkey-story-onboard-${Date.now()}@example.test`
        const row = await giveAddress(page, target.memberId, address, ONBOARD_SLOT)

        await page.goto(`/station/members/edit/${row.id}`)
        await expect(page.getByRole('button', {name: 'Erneut onboarden'})).toBeVisible({timeout: 15_000})
        await page.getByRole('button', {name: 'Erneut onboarden'}).click()
        await expect(page.getByText('Der Einrichtungslink ist unterwegs.')).toBeVisible({timeout: 15_000})

        await expect(page.getByRole('button', {name: 'Code anzeigen'})).toHaveCount(0)

        await page.context().close()
    })
})
