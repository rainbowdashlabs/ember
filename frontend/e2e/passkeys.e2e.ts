/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    answerStepUpPrompts,
    apiHeaders,
    demoAccounts,
    DEMO_PASSWORD,
    expect,
    freshStepUpProof,
    pageAs,
    pageAsThrowaway,
    pinnedRole,
    test,
    type DemoAccount,
} from './fixtures/auth'
import type {APIRequestContext, Browser, CDPSession, Page} from '@playwright/test'
import {cast, passkeySlot, spokenForMemberIds, type CastMember} from './fixtures/cast'

/**
 * The passkey stories, over Chromium's virtual authenticator: the only way to prove any of this
 * without a finger. Chromium only; the other projects skip.
 *
 * Every story takes a throwaway account of its own: a passkey on a shared role would follow the
 * other stories around, and several of these end sessions or refuse passwords on purpose.
 */
test.describe('Passkeys', () => {
    test.skip(({browserName}) => browserName !== 'chromium', 'the virtual authenticator is CDP-only')
    // Several ceremonies, a step-up round trip and full sign-ins chain up in one story, and the
    // stagger that keeps the proofs off the throttle costs its seconds too.
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
     */
    async function giveAddress(manager: Page, memberId: number, address: string, slot: number): Promise<MemberRow> {
        await manager.waitForTimeout(slot * 6_000)
        const headers = await apiHeaders(manager)
        const list = await manager.request.get('/api/v1/station-members', {headers})
        // By id, never by the address: the address is what this is about to overwrite, so a row
        // found by it is a row that has not been written yet and cannot be found twice.
        const row = (await list.json() as MemberRow[]).find(candidate => candidate.id === memberId)
        if (!row) throw new Error(`Member ${memberId} is not in the station list`)
        const put = () => manager.request.put(`/api/v1/members/${row.accountId}`, {
            headers,
            data: {email: address, firstName: row.firstName, lastName: row.lastName},
        })
        // Proved only when refused: the retried write usually goes through on the freshness a
        // sibling story's proof left behind, without spending a code of its own.
        let saved = await put()
        for (let attempt = 0; saved.status() === 401 && attempt < 3; attempt++) {
            try {
                await freshStepUpProof(manager)
            } catch {
                // The code was spent or the throttle answered: the next period brings a fresh
                // code and the throttle a free slot, so the wait is the whole remedy.
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

    /** Walks the security screen's creation flow to the finished trial. */
    async function createPasskey(page: Page): Promise<void> {
        await page.goto('/account/security')
        await page.getByRole('button', {name: 'Passkey einrichten'}).click()
        // The creation stands behind the fresh-proof check; the fixture answers the dialog.
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
     */
    async function signInWithPasskey(page: Page): Promise<void> {
        await page.goto('/login')
        const shell = page.getByTestId('app-shell')
        const button = page.getByRole('button', {name: 'Mit Passkey anmelden'})
        await expect(shell.or(button).first()).toBeVisible({timeout: 20_000})
        if (await shell.count() === 0) {
            // The click can lose a last-moment race against the autofill navigating away, and the
            // timeout is what lets it lose. Nothing sets an action timeout, so the default of none
            // applies: a click whose target keeps moving out from under it is retried for as long
            // as it takes, never rejects, and the catch below never runs. The story then sits here
            // until its own budget ends, with a page that looks perfectly signed in.
            await button.click({timeout: 10_000}).catch(() => {})
        }
        await expect(shell).toBeVisible({timeout: 20_000})
    }

    test('a passkey is created, tried, and signs its owner in', async ({browser, request}) => {
        const account = await storyAccount(0)

        // The context is built by hand: the throwaway fixture plants its session through an init
        // script that runs on every load, which would put the token back the moment this story
        // signs out to prove the passkey alone gets in.
        const login = await request.post('/api/v1/demo/login', {data: {email: account.email}})
        if (!login.ok()) throw new Error(`Demo login for ${account.email} answered ${login.status()}`)
        const {token} = await login.json() as {token: string}

        const context = await browser.newContext()
        const page = await context.newPage()
        await page.addInitScript(() => window.localStorage.setItem('storage_consent', 'accepted'))
        const {cdp, authenticatorId} = await addAuthenticator(page)
        await answerStepUpPrompts(page)
        await page.goto('/login')
        await page.evaluate(([sessionToken, stationId]) => {
            window.localStorage.setItem('session_token', sessionToken ?? '')
            if (stationId) window.localStorage.setItem('station_id', stationId)
        }, [token, account.stationId ?? ''])

        await createPasskey(page)
        await expect(page.getByText('Anmeldung', {exact: true})).toBeVisible()

        // The credential survives into a fresh sign-in: sign out by clearing the session, then
        // come back in with nothing but the passkey.
        await page.evaluate(() => window.localStorage.removeItem('session_token'))
        await signInWithPasskey(page)

        // A password sign-in afterwards asks nothing extra: the password path is untouched (D3).
        // The authenticator goes away first: it answers the login screen's passkey autofill on
        // its own, and that sign-in would win the race against the password form being filled.
        await cdp.send('WebAuthn.removeVirtualAuthenticator', {authenticatorId})
        await page.evaluate(() => window.localStorage.removeItem('session_token'))
        await page.goto('/login')
        await page.getByPlaceholder('E-Mail oder Benutzername').fill(account.email)
        await page.getByPlaceholder('Passwort').fill(DEMO_PASSWORD)
        await page.getByRole('button', {name: 'Anmelden', exact: true}).click()
        await expect(page.getByTestId('app-shell')).toBeVisible({timeout: 20_000})

        await context.close()
    })

    test('switching the password off refuses it, removing the last passkey opens it again', async ({browser, request}) => {
        // The switch is only offered to somebody the way back in can be mailed to.
        const account = await addressedStoryAccount(browser, request, 1)
        const page = await pageAsThrowaway(browser, request, [], account)
        await addAuthenticator(page)

        await createPasskey(page)

        // The switch appears only once a passkey has proven itself, and it stands behind the
        // fresh-proof check like everything else on this screen. Its accessible name starts with
        // the label and carries the hint after it.
        await page.getByRole('switch', {name: /^Anmeldung mit Passwort/}).click()
        await expect(page.getByText('Die Anmeldung mit Passwort ist ausgeschaltet.', {exact: false}))
            .toBeVisible({timeout: 15_000})

        // The password is now refused at the door, with the ways back in named. Tried from a
        // sessionless context: a demo login here would replace the one session the account has,
        // which is the one the first page still needs.
        const freshContext = await browser.newContext()
        const fresh = await freshContext.newPage()
        await fresh.addInitScript(() => window.localStorage.setItem('storage_consent', 'accepted'))
        await fresh.goto('/login')
        await fresh.getByPlaceholder('E-Mail oder Benutzername').fill(account.email)
        await fresh.getByPlaceholder('Passwort').fill(DEMO_PASSWORD)
        await fresh.getByRole('button', {name: 'Anmelden', exact: true}).click()
        await expect(fresh.getByText('Die Anmeldung mit Passwort ist für dieses Konto ausgeschaltet', {exact: false}))
            .toBeVisible({timeout: 15_000})
        await freshContext.close()

        // Removing the last passkey is the safety valve: the password door opens again, visibly.
        await page.goto('/account/security')
        await page.getByRole('button', {name: 'Löschen'}).first().click()
        await page.getByRole('dialog').getByRole('button', {name: 'Löschen'}).click()
        await expect(page.getByText('Die Anmeldung mit Passwort ist wieder eingeschaltet.', {exact: false}))
            .toBeVisible({timeout: 15_000})

        await page.context().close()
    })

    test('the device handshake frees a device across two contexts', async ({browser, request}) => {
        const account = await storyAccount(2)

        // The signed-in device, which will approve.
        const approver = await pageAsThrowaway(browser, request, [], account)

        // The new device: no session, only a virtual authenticator of its own.
        const newContext = await browser.newContext()
        const newDevice = await newContext.newPage()
        await newDevice.addInitScript(() => window.localStorage.setItem('storage_consent', 'accepted'))
        await addAuthenticator(newDevice)

        await newDevice.goto('/unlock-device')
        // A browser that can hold a passkey is asked which it wants; this story wants the credential.
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

        // The new device enrols and signs in with the passkey it just made.
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
     */
    test('a device with no credential is signed in by one that already holds a session', async ({browser, request}) => {
        // A slot of its own: the approval screen is throttled per account, and sharing one with the
        // enrolment story above made the second of the two meet a refusal instead of a code.
        const account = await storyAccount(4)
        const approver = await pageAsThrowaway(browser, request, [], account)

        // Deliberately no authenticator on this context: nothing here can hold a passkey.
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

        // The screen says what this grant is, which is a different thing from a passkey.
        await expect(approver.getByText('Das Gerät wird danach in deinem Namen angemeldet', {exact: false}))
            .toBeVisible()
        await approver.getByTestId(`number-choice-${handshake.matchNumber}`).click()
        await expect(approver.getByText('Freigeschaltet.', {exact: false})).toBeVisible({timeout: 15_000})

        await expect(newDevice.getByTestId('app-shell')).toBeVisible({timeout: 30_000})

        // And nothing was left behind: the account holds no passkey it did not have before.
        const headers = await apiHeaders(newDevice)
        const status = await newDevice.request.get('/api/v1/account/passkeys', {headers});
        expect((await status.json()).passkeys ?? [], 'a sign-in leaves no credential on the device').toHaveLength(0)

        await approver.context().close()
        await newContext.close()
    })

    test('the offer appears once after a sign-in and Nein danke ends it', async ({browser, request}) => {
        // The offer never goes to somebody the way back in cannot be mailed to.
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

        // Declined for good: the next sign-in goes straight through.
        await page.evaluate(() => window.localStorage.removeItem('session_token'))
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
     * people who could actually sign in.
     */
    test('a guardian signs a member in their care in on a borrowed machine', async ({browser, request}) => {
        // Nobody another story is acting as, on either side. Signing in replaces a dev session row
        // and giving a managed member an address ends their sessions outright, so a guardian or a
        // charge that is also a shared role or another story's part would take that story's session
        // out from under it in the middle of the run. Both are settled by id at global setup.
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

        // Something to sign in with, and permission to. A managed member is seeded with neither, and
        // the approval screen offers only the people for whom both are true. A name rather than an
        // address on purpose: a dev session token is the account's address, so giving one out would
        // sign this member out of wherever else they are, which is the trap the choice above avoids.
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
        // Named as the charge, which is who is signing in. The guardian may answer it because the
        // charge is in their care, and that is the only reason anybody but the charge may.
        const handshake = await raiseRequest(newDevice, loginName)

        await guardian.goto('/account/unlock-device')
        await guardian.getByPlaceholder('K7RM-2WQD').fill(handshake.code)
        await guardian.getByRole('button', {name: 'Code prüfen'}).click()

        // The choice the guardian gets and nobody else does: whose sign-in this is.
        const forWhom = guardian.getByTestId('approve-for')
        await expect(forWhom).toBeVisible({timeout: 15_000})
        await forWhom.selectOption(String(charge.accountId))
        await guardian.getByTestId(`number-choice-${handshake.matchNumber}`).click()
        await expect(guardian.getByText('Freigeschaltet.', {exact: false})).toBeVisible({timeout: 15_000})

        // The device is signed in as the charge, not as the guardian who approved it.
        await expect(newDevice.getByTestId('app-shell')).toBeVisible({timeout: 30_000})
        const session = await newDevice.request
            .get('/api/v1/session', {headers: await apiHeaders(newDevice)})
            .then(response => response.json())
        expect(session.account.username).toBe(loginName)
        expect(session.member.id, 'the device holds the charge, not the guardian').not.toBe(guardianCast.memberId)

        await newContext.close()
        await guardian.context().close()
    })

    test('a manager onboards a member again and gets a passkey code for an addressless one', async ({browser, request}) => {
        // The shared manager session acts here; a fresh login as the manager would replace it
        // under every other story. The target is a slot of its own, because onboarding again
        // ends the target's sessions.
        const target = await storyAccount(ONBOARD_SLOT)
        const page = await pageAs(browser, 'manager')

        const address = `passkey-story-onboard-${Date.now()}@example.test`
        const row = await giveAddress(page, target.memberId, address, ONBOARD_SLOT)

        await page.goto(`/station/members/edit/${row.id}`)
        await expect(page.getByRole('button', {name: 'Erneut onboarden'})).toBeVisible({timeout: 15_000})
        await page.getByRole('button', {name: 'Erneut onboarden'}).click()
        await expect(page.getByText('Der Einrichtungslink ist unterwegs.')).toBeVisible({timeout: 15_000})

        // A member with an address of their own is never offered the code button: the mail path
        // is theirs, and it is the one with a second party in it.
        await expect(page.getByRole('button', {name: 'Code anzeigen'})).toHaveCount(0)

        await page.context().close()
    })
})
