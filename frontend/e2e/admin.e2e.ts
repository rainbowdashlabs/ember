/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Browser, Page} from '@playwright/test'
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * The administration area: the legal documents an operator publishes, the settings that govern the
 * instance, the applications and reports that arrive from outside it, and the pages that only have
 * to render.
 *
 * Two of the four groups run serial on purpose. Legal documents and instance settings are
 * instance-wide state - documents on disk, settings in one row - so two workers editing them at
 * once would each assert on the other's save. The pages that only render are left parallel, which
 * is where most of the wall-clock sits.
 *
 * What an anonymous visitor sees is asserted on the public page rather than in the admin preview
 * throughout. The editor previews client-side, so an assertion there would pass against a backend
 * that never wrote anything.
 */
const DOCUMENTS = {
    privacy: 'Datenschutz',
    tos: 'Nutzungsbedingungen',
    consent: 'Einwilligung',
    imprint: 'Impressum',
} as const

/** The section name Ember reserves for the disclosure it generates itself. */
const GENERATED_SECTION = 'Speicherung im Browser (automatisch)'

async function openDocument(page: Page, document: keyof typeof DOCUMENTS) {
    await page.goto('/admin/settings/legal')
    await page.getByRole('button', {name: DOCUMENTS[document], exact: true}).click()
    await expect(page.getByRole('button', {name: 'Vorlage laden'}).first()).toBeVisible()
}

/**
 * The text of every editable section.
 *
 * Read from the elements rather than matched with `hasText`: the editor binds the markdown as a
 * value, and a textarea's value is not its text content, so a text-based locator finds nothing
 * however right the page is.
 */
async function sectionContents(page: Page): Promise<string> {
    const values = await page.locator('textarea').evaluateAll(
        nodes => nodes.map(node => (node as HTMLTextAreaElement).value))
    return values.join('\n\n')
}

async function loadEveryTemplate(page: Page) {
    await page.getByRole('button', {name: 'Vorlage laden'}).first().click()
    await expect(page.getByText('Mitgelieferte Vorlage laden')).toBeVisible()
    await page.getByRole('button', {name: 'Alle auswählen'}).click()
    await page.getByRole('button', {name: 'Auswahl laden'}).click()
    await expect(page.getByText('Mitgelieferte Vorlage laden')).toHaveCount(0)
}

async function save(page: Page) {
    await page.getByRole('button', {name: 'Speichern', exact: true}).first().click()
    await expect(page.getByRole('button', {name: 'Gespeichert'})).toBeVisible()
}

/** Whether the instance currently lets anyone apply for a station, straight from the server. */
async function stationRegistrationEnabled(adminPage: Page): Promise<boolean> {
    const response = await adminPage.request.get('/api/v1/public/settings/station-registration')
    return (await response.json()).enabled
}

/**
 * Drives the public application form to open or closed.
 *
 * Whether it is open is instance-wide state that one story switches off and on again. Asking for
 * the state rather than assuming it is what keeps the stories from depending on the order the
 * suite happens to run them in, or on what a previous run left behind.
 */
/**
 * Asserts what the public application form shows, as a visitor who arrives now.
 *
 * A fresh context each time, not a reload. The page reads the setting once when it loads and the
 * answer carries no cache headers, so a browser that has already asked keeps its first answer for
 * the life of the context - reloading the same page would test that cache rather than the setting.
 */
async function expectApplyPage(browser: Browser, expected: 'open' | 'closed') {
    const context = await browser.newContext()
    try {
        const page = await context.newPage()
        await page.goto('/apply')
        await expect(expected === 'open'
            ? page.getByRole('button', {name: 'Antrag absenden'})
            : page.getByText('Die Registrierung neuer Wachen ist derzeit deaktiviert.')).toBeVisible()
    } finally {
        await context.close()
    }
}

async function setStationRegistration(adminPage: Page, enabled: boolean) {
    const current = await stationRegistrationEnabled(adminPage)
    if (current === enabled) return

    await adminPage.goto('/admin/settings')
    const toggle = adminPage.getByRole('switch', {name: 'Wachenregistrierung'})

    // The panel renders before its values arrive, so the switch shows its default first. Waiting
    // for it to show what the server actually holds is what stops the click below from being read
    // off a stale rendering and sending the setting the other way.
    await expect(toggle).toHaveAttribute('aria-checked', String(current))
    await toggle.click()
    await expect(toggle).toHaveAttribute('aria-checked', String(enabled))
    await expect.poll(() => stationRegistrationEnabled(adminPage)).toBe(enabled)
}

test.describe('Legal documents', () => {
    test.describe.configure({mode: 'serial'})

    /**
     * An operator edits a legal text and an anonymous visitor reads it. The edit has to travel all
     * the way out: the admin editor renders
     * its own preview client-side, so asserting there would pass against a backend that never
     * wrote the file.
     */
    test('an edited legal text reaches the public page', async ({adminPage, page}) => {
        const marker = unique('Rechtstext')

        await openDocument(adminPage, 'imprint')
        const section = adminPage.locator('textarea').first()
        await section.fill(`${await section.inputValue()}\n\n${marker}`)
        await save(adminPage)

        await page.goto('/imprint')
        await expect(page.getByText(marker)).toBeVisible()
    })

    /**
     * Every document type has to offer something, in both languages Ember ships. A type that
     * silently offers nothing is the failure mode this catches - it looks like an empty list rather
     * than an error, and an operator would read it as "there is no template for this".
     */
    test('every shipped document offers its sections in both languages', async ({adminPage: page}) => {
        for (const document of Object.keys(DOCUMENTS) as (keyof typeof DOCUMENTS)[]) {
            for (const locale of ['DE', 'EN']) {
                await openDocument(page, document)
                await page.getByRole('button', {name: locale, exact: true}).click()
                await page.getByRole('button', {name: 'Vorlage laden'}).first().click()

                await expect(page.getByText('Für diese Sprache liefert Ember keine Vorlage mit.')).toHaveCount(0)
                await expect(page.getByRole('button', {name: 'Alle auswählen'})).toBeVisible()

                await page.getByRole('button', {name: 'Abbrechen'}).click()
            }
        }
    })

    test('loading the shipped template fills the editor and publishes', async ({adminPage, page}) => {
        await openDocument(adminPage, 'tos')
        await loadEveryTemplate(adminPage)

        expect(await sectionContents(adminPage)).toContain('# Nutzungsbedingungen')
        expect(await sectionContents(adminPage)).toContain('§ 1 Geltungsbereich')
        await save(adminPage)

        await page.goto('/terms')
        await expect(page.getByRole('heading', {name: '§ 1 Geltungsbereich'})).toBeVisible()
    })

    /**
     * Loading a template is not "start again". It replaces what it brings and leaves everything
     * else standing, which is what makes it safe to reach for on an instance that has already been
     * written in.
     */
    test('loading a template leaves sections it does not carry alone', async ({adminPage: page}) => {
        const ownSection = unique('eigener-abschnitt')

        await openDocument(page, 'tos')
        await page.getByRole('button', {name: 'Datei hinzufügen'}).first().click()
        await page.getByRole('textbox').last().fill(ownSection)
        await page.getByRole('button', {name: 'Datei hinzufügen'}).last().click()
        await expect(page.getByText(ownSection)).toBeVisible()

        await loadEveryTemplate(page)

        await expect(page.getByText(ownSection)).toBeVisible()
        expect(await sectionContents(page)).toContain('# Nutzungsbedingungen')
    })

    /**
     * The generated disclosure is the one section an administrator may not write. It has to be
     * there, carry the keys the application really uses, and offer no way to edit or delete it -
     * a text that can be edited is a text that can go out of step with the software.
     */
    test('the browser storage section is generated and read-only', async ({adminPage, page}) => {
        await openDocument(adminPage, 'privacy')
        await loadEveryTemplate(adminPage)
        await save(adminPage)

        await expect(adminPage.getByText(GENERATED_SECTION)).toBeVisible()
        await expect(adminPage.getByText('session_token')).toBeVisible()
        expect(await sectionContents(adminPage)).not.toContain('session_token')

        await page.goto('/privacy')
        await expect(page.getByRole('heading', {name: 'Speicherung im Browser'})).toBeVisible()
        await expect(page.getByText('session_token')).toBeVisible()
    })

    /**
     * The consent text carries the same generated section. It is a separate document with its own
     * version, so covering the privacy policy says nothing about it.
     */
    test('the consent text carries the generated storage section too', async ({adminPage: page}) => {
        await openDocument(page, 'consent')
        await loadEveryTemplate(page)

        await expect(page.getByText(GENERATED_SECTION)).toBeVisible()
        await expect(page.getByText('storage_consent')).toBeVisible()
    })

    /**
     * The shipped imprint is meant to be filled in rather than rewritten. What proves it is a value
     * entered once reaching the public page - the substitution happens on the server, so the
     * editor showing the right thing would prove nothing.
     */
    test('a placeholder from the shipped imprint is filled in and published', async ({adminPage, page}) => {
        const operator = unique('Jugendfeuerwehr')

        await openDocument(adminPage, 'imprint')
        await loadEveryTemplate(adminPage)
        await save(adminPage)

        await adminPage.getByRole('button', {name: 'Anzeigen'}).click()
        await expect(adminPage.getByText('{{ betreiber.name }}')).toBeVisible()

        await adminPage.getByRole('textbox', {name: 'betreiber.name'}).fill(operator)
        await adminPage.getByRole('button', {name: 'Speichern', exact: true}).last().click()
        await expect(adminPage.getByRole('button', {name: 'Gespeichert'})).toBeVisible()

        await page.goto('/imprint')
        await expect(page.getByText(operator)).toBeVisible()
        await expect(page.getByText('{{ betreiber.name }}')).toHaveCount(0)
    })

    /**
     * A placeholder nobody filled in stays where it is. Blanking it would swallow the operator's
     * address without anyone noticing; standing there is what makes the omission visible.
     */
    test('a placeholder without a value stays visible', async ({adminPage, page}) => {
        await openDocument(adminPage, 'imprint')
        await loadEveryTemplate(adminPage)
        await save(adminPage)

        await adminPage.getByRole('button', {name: 'Anzeigen'}).click()
        await adminPage.getByRole('textbox', {name: 'betreiber.telefon'}).fill('')
        await adminPage.getByRole('button', {name: 'Speichern', exact: true}).last().click()
        await expect(adminPage.getByRole('button', {name: 'Gespeichert'})).toBeVisible()

        await page.goto('/imprint')
        await expect(page.getByText('{{ betreiber.telefon }}')).toBeVisible()
    })
})

/**
 * Creating a station, taking in an application, changing a setting, configuring the password check
 * and reading the token configuration.
 *
 * Serial, because three of them change instance-wide state: registration is switched off and on
 * again, and an application cannot be submitted while it is off.
 */
test.describe('Instance administration', () => {
    test.describe.configure({mode: 'serial'})

    test('a station is created by the operator', async ({adminPage: page}) => {
        const station = unique('Wache')

        await page.goto('/admin/stations')
        await page.getByText('Neue Wache').click()
        await page.getByPlaceholder('Name der Wache').fill(station)
        await page.getByRole('button', {name: 'Erstellen'}).click()

        await page.goto('/admin/stations')
        await expect(page.getByText(station)).toBeVisible()
    })

    /**
     * The whole way through: an anonymous applicant, the confirmation link, and the operator
     * accepting. The token is taken from the submission response rather than from an inbox - the
     * instance under test sends no mail, and the story is about the flow, not about the delivery.
     */
    test('a station application is submitted, confirmed and accepted', async ({page, adminPage}) => {
        const station = unique('Antragswache')
        const applicant = `${unique('bewerber').toLowerCase()}@example.invalid`

        await setStationRegistration(adminPage, true)

        await page.goto('/apply')
        await page.getByPlaceholder('Max', {exact: true}).fill('Test')
        await page.getByPlaceholder('Mustermann', {exact: true}).fill('Antragsteller')
        await page.getByPlaceholder(/@feuerwehr-musterstadt\.de/).fill(applicant)
        await page.getByPlaceholder('Freiwillige Feuerwehr Musterstadt').fill(station)

        const submission = page.waitForResponse(response =>
            response.url().endsWith('/station-applications') && response.request().method() === 'POST')
        await page.getByRole('button', {name: 'Antrag absenden'}).click()
        const {verificationToken} = await (await submission).json()
        await expect(page.getByText(/Dein Antrag wurde eingereicht/)).toBeVisible()

        await page.goto(`/apply/verify?token=${verificationToken}`)
        await expect(page.getByText(/Deine E-Mail-Adresse wurde bestätigt/)).toBeVisible()

        await adminPage.goto('/admin/stations/applications')
        const entry = adminPage.getByTestId('application-entry').filter({hasText: station})
        await expect(entry).toBeVisible()
        await entry.getByRole('button', {name: 'Annehmen'}).click()

        await adminPage.goto('/admin/stations')
        await expect(adminPage.getByText(station)).toBeVisible()
    })

    /**
     * A setting is only worth anything where it is used. Switching station registration off has to
     * close the public application form, not merely flip a toggle in the admin area - so the
     * assertion is made by an anonymous visitor.
     */
    test('a changed instance setting takes effect where it is used', async ({adminPage, browser}) => {
        try {
            await setStationRegistration(adminPage, false)
            await expectApplyPage(browser, 'closed')
        } finally {
            await setStationRegistration(adminPage, true)
        }

        await expectApplyPage(browser, 'open')
    })

    /**
     * The configuration, not the check itself. Asserting that a breached password is refused would
     * mean calling Have I Been Pwned from the test run - a third party, over the network, on every
     * run. What is worth holding here is that the switch survives a reload.
     */
    test('compromised-password checking is configured and survives a reload', async ({adminPage: page}) => {
        const toggle = page.getByRole('switch', {name: 'HIBP-Prüfung aktiviert'})

        await page.goto('/admin/settings/security/hibp')
        await expect(toggle).toBeVisible()
        const before = await toggle.getAttribute('aria-checked')

        try {
            await toggle.click()
            await page.getByRole('button', {name: 'Speichern', exact: true}).click()
            await expect(page.getByRole('button', {name: 'Gespeichert'})).toBeVisible()

            await page.reload()
            await expect(toggle).toHaveAttribute('aria-checked', before === 'true' ? 'false' : 'true')
        } finally {
            if (await toggle.getAttribute('aria-checked') !== before) {
                await toggle.click()
                await page.getByRole('button', {name: 'Speichern', exact: true}).click()
                await expect(page.getByRole('button', {name: 'Gespeichert'})).toBeVisible()
            }
        }
    })

    /**
     * The page configures how tokens are generated and how long they last, and reports whether the
     * server-side pepper is set. That
     * badge is the part worth holding - without a pepper, stored tokens are unsalted hashes.
     */
    test('the token configuration and the pepper state are shown', async ({adminPage: page}) => {
        await page.goto('/admin/settings/security/tokens')

        await expect(page.getByText('Serverseitige Schlüssel für Token-Speicherung')).toBeVisible()
        await expect(page.getByRole('heading', {name: 'Tokens & Sitzungen'})).toBeVisible()
        await expect(page.getByText(/^(Konfiguriert|Nicht konfiguriert)$/)).toBeVisible()
        await expect(page.getByRole('spinbutton').first()).not.toHaveValue('')
    })
})

/**
 * A member reports a problem and the operator triages it. Two actors, so it stays out of the serial
 * groups and takes both pages at once.
 */
test.describe('Problem reports', () => {
    test('a reported problem reaches the operator and is acknowledged', async ({memberPage, adminPage}) => {
        const complaint = unique('Meldung aus dem Test')

        await memberPage.goto('/station/dashboard/overview')
        await memberPage.getByRole('button', {name: 'Problem melden'}).click()
        await memberPage.getByPlaceholder('Was funktioniert nicht wie erwartet?').fill(complaint)
        await memberPage.getByRole('button', {name: 'Absenden'}).click()
        await expect(memberPage.getByText(/Deine Meldung wurde erfolgreich gesendet/)).toBeVisible()

        await adminPage.goto('/admin/monitoring/problem-reports')
        const report = adminPage.getByTestId('problem-report').filter({hasText: complaint})
        await expect(report).toBeVisible()

        // Acknowledging is what takes a report off the operator's desk, so the open list has to
        // drop it. That it was acknowledged rather than lost is what the second half asserts.
        await report.getByRole('button', {name: 'Bestätigen'}).click()
        await expect(report).toHaveCount(0)

        await adminPage.getByRole('switch', {name: 'Bestätigte anzeigen'}).click()
        await expect(report).toBeVisible()
        await expect(report.getByText('Bestätigt')).toBeVisible()
    })
})

/**
 * The monitoring pages, the operator dashboard and the data tracking inventory - the pages that
 * only have to render.
 *
 * Each is asserted on its subtitle rather than its title: the title is repeated by the sidebar
 * link that leads to the page, so a page that never loaded would still show it and the assertion
 * would pass against a blank screen. Every one of them also has to come up without the generic
 * error banner, which is what a failing endpoint behind the page looks like.
 */
test.describe('Admin pages render', () => {
    const ERROR_BANNER = 'Ein Fehler ist aufgetreten. Bitte versuche es erneut.'

    const MONITORING = [
        {path: '/admin/monitoring/api-status', subtitle: 'Anfrage- und Fehlerstatistiken'},
        {path: '/admin/monitoring/traffic', subtitle: 'Anfrage- und Datenverkehr pro Wache'},
        {path: '/admin/monitoring/storage', subtitle: 'Speichernutzung und Kontingente aller Wachen verwalten'},
        {path: '/admin/monitoring/discovery', subtitle: 'Bekannte Peer-Instanzen'},
        {path: '/admin/monitoring/feed-metrics', subtitle: 'Nutzung der RSS- und iCal-Feeds'},
        {path: '/admin/monitoring/maps', subtitle: 'Tile-Provider und Kartenoptionen'},
    ]

    for (const {path, subtitle} of MONITORING) {
        test(`the monitoring page ${path} renders`, async ({adminPage: page}) => {
            await page.goto(path)

            await expect(page.getByText(subtitle)).toBeVisible()
            await expect(page.getByText(ERROR_BANNER)).toHaveCount(0)
        })
    }

    test('the operator dashboard renders', async ({adminPage: page}) => {
        await page.goto('/admin/dashboard/overview')
        await expect(page.getByText('Administration auf einen Blick')).toBeVisible()
        await expect(page.getByText(/Alles erledigt|Erfordert Aufmerksamkeit/).first()).toBeVisible()

        await page.goto('/admin/dashboard/statistics')
        await expect(page.getByText('Systemweite Auswertungen')).toBeVisible()
        await expect(page.getByText('Plattform', {exact: true})).toBeVisible()
        await expect(page.getByText('Mit Zwei-Faktor')).toBeVisible()
        await expect(page.getByText('Kommende Termine')).toBeVisible()
        await expect(page.getByText('Anmeldungen', {exact: true})).toBeVisible()
        await expect(page.getByText(ERROR_BANNER)).toHaveCount(0)
    })

    /**
     * The inventory is a dev-mode view on both sides: the routes behind it are registered only
     * when the backend runs in dev mode, and the page itself renders its "not in this build"
     * notice unless the frontend is served by the dev server. The suite runs against both, which
     * the Playwright config already requires - if that ever changes, this failing is the point.
     */
    test('the data tracking inventory lists its tables', async ({adminPage: page}) => {
        await page.goto('/admin/dev/data-tracking')

        await expect(page.getByText('Nur im Dev-Modus verfügbar')).toBeVisible()
        await expect(page.getByText('Tabellen gesamt')).toBeVisible()
        await expect(page.getByText('account', {exact: true}).first()).toBeVisible()
    })
})
