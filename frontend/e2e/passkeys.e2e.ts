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
     * The members these stories may act as, in a fixed order, minus the two accounts the shared
     * role sessions belong to. Each story owns one slot: the suite runs fully parallel across
     * workers, so nothing mutable can hand out accounts, and two stories acting as the same
     * person would wipe each other's sessions - several of these end sessions on purpose.
     */
    async function storyCandidates(request: Parameters<typeof demoAccounts>[0]): Promise<DemoAccount[]> {
        const [accounts, manager, member] = await Promise.all([
            demoAccounts(request), pinnedRole('manager'), pinnedRole('member'),
        ])
        const reserved = [manager.email, member.email]
        return accounts
            .filter(candidate =>
                candidate.userType === 'MEMBER'
                && !!candidate.email
                // The station the roles were pinned to at global setup: a station made mid-run by
                // importing a transfer holds the same accounts again under its own station id,
                // and a pool computed against it would hand out different slots.
                && candidate.stationId === manager.stationId
                // Only the seeded synthetic addresses: accounts the stories create carry other
                // domains, so this pool can only ever shrink during a run. A shrinking pool is
                // what makes a fixed slot number pick a distinct person in every worker,
                // however the timing falls.
                && candidate.email.endsWith('.local')
                && !reserved.includes(candidate.email)
                && !candidate.permissions.includes('STATION_MANAGER'))
            .sort((a, b) => a.email.localeCompare(b.email))
    }

    /** How many slots the independent stories occupy; the onboarding story picks past them. */
    const STORY_SLOTS = 4

    async function storyAccount(request: APIRequestContext, slot: number): Promise<DemoAccount> {
        const account = (await storyCandidates(request))[slot]
        if (!account) throw new Error(`No member account for passkey story slot ${slot}`)
        return account
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
    async function addressedStoryAccount(browser: Browser, request: APIRequestContext, slot: number): Promise<DemoAccount> {
        const account = await storyAccount(request, slot)
        const manager = await pageAs(browser, 'manager')
        try {
            const address = `passkey-story-${slot}-${Date.now()}@example.test`
            await giveAddress(manager, account.email, address, slot)
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
    async function giveAddress(manager: Page, currentEmail: string, address: string, slot: number): Promise<MemberRow> {
        await manager.waitForTimeout(slot * 6_000)
        const headers = await apiHeaders(manager)
        const list = await manager.request.get('/api/v1/station-members', {headers})
        const row = (await list.json() as MemberRow[]).find(candidate => candidate.email === currentEmail)
        if (!row) throw new Error('The story member is not in the station list')
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
            // The click can lose a last-moment race against the autofill navigating away.
            await button.click().catch(() => {})
        }
        await expect(shell).toBeVisible({timeout: 20_000})
    }

    test('a passkey is created, tried, and signs its owner in', async ({browser, request}) => {
        const account = await storyAccount(request, 0)

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
            window.localStorage.setItem('session_token', sessionToken)
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
        const account = await storyAccount(request, 2)

        // The signed-in device, which will approve.
        const approver = await pageAsThrowaway(browser, request, [], account)

        // The new device: no session, only a virtual authenticator of its own.
        const newContext = await browser.newContext()
        const newDevice = await newContext.newPage()
        await newDevice.addInitScript(() => window.localStorage.setItem('storage_consent', 'accepted'))
        await addAuthenticator(newDevice)

        await newDevice.goto('/unlock-device')
        // Waited for by content, not visibility: the element renders before the code arrives,
        // and an empty read here would be pasted into a form that rightly refuses it.
        const codeElement = newDevice.locator('.font-mono').first()
        await expect(codeElement).toHaveText(/[0-9A-Z-]{8,9}/, {timeout: 15_000})
        const code = (await codeElement.innerText()).trim()

        await approver.goto('/account/unlock-device')
        await approver.getByPlaceholder('K7RM-2WQD').fill(code)
        await approver.getByRole('button', {name: 'Code prüfen'}).click()
        await expect(approver.getByText('Nur freischalten, wenn du gerade selbst an diesem Gerät sitzt.'))
            .toBeVisible()
        await approver.getByRole('button', {name: 'Freischalten', exact: true}).click()
        await expect(approver.getByText('Freigeschaltet.', {exact: false})).toBeVisible({timeout: 15_000})

        // The new device enrols and signs in with the passkey it just made.
        await expect(newDevice.getByTestId('app-shell')).toBeVisible({timeout: 30_000})

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

    test('a manager onboards a member again and gets a passkey code for an addressless one', async ({browser, request}) => {
        // The shared manager session acts here; a fresh login as the manager would replace it
        // under every other story. The target is a slot of its own, because onboarding again
        // ends the target's sessions.
        const target = await storyAccount(request, STORY_SLOTS)
        const page = await pageAs(browser, 'manager')

        const address = `passkey-story-onboard-${Date.now()}@example.test`
        const row = await giveAddress(page, target.email, address, STORY_SLOTS)

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
