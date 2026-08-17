/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect} from './fixtures/auth'
import {sidebar, sidebarEntry} from './fixtures/sidebar'

/**
 * What switching a module off actually does to the person using the station: the part of the
 * sidebar it owns goes away, and comes back when the module does. A station with a module off has
 * to stop offering it, or the reader is sent to a page that refuses them.
 *
 * Two things make these stories safe to run beside the rest of the suite. They act on a *second*
 * station — the seeded partner — because a station missing its inventory for a second is a station
 * every other story would trip over. And they run one after another within this file: the setting
 * is one list of disabled modules, so two stories toggling at once would each overwrite what the
 * other just wrote.
 */
test.describe.configure({mode: 'serial'})

/** A module, and the entry it owns in the sidebar. */
interface ModuleEntry {
    /** The key the station sends to the server. */
    key: string
    /** What the module is called on the settings page, for readable story names. */
    name: string
    /** The sidebar entries the module owns, as they read to whoever looks at the sidebar. */
    entries: string[]
    /**
     * Where the sidebar shows those entries. Top-level entries show anywhere; a subpoint only
     * renders while its group is open, and a group opens on the routes it holds.
     */
    at: string
}

const OVERVIEW = '/station/dashboard/overview'

const MODULES: ModuleEntry[] = [
    {key: 'INVENTORY', name: 'inventory', entries: ['Inventar'], at: OVERVIEW},
    {key: 'NEWS', name: 'news', entries: ['Neuigkeiten'], at: OVERVIEW},
    {key: 'EVENTS', name: 'events', entries: ['Termine'], at: OVERVIEW},
    {key: 'ATTENDANCE', name: 'attendance', entries: ['Anwesenheit'], at: OVERVIEW},
    {key: 'FORMS', name: 'forms', entries: ['Formulare'], at: OVERVIEW},
    {key: 'LOST_AND_FOUND', name: 'lost and found', entries: ['Fundbüro'], at: OVERVIEW},
    {key: 'KNOWLEDGE_BASE', name: 'knowledge base', entries: ['Wiki'], at: OVERVIEW},
    {key: 'BOARDS', name: 'boards', entries: ['Boards'], at: OVERVIEW},
    {key: 'PROCEDURES', name: 'procedures', entries: ['Abläufe'], at: OVERVIEW},
    {key: 'WAITING_LIST', name: 'waiting lists', entries: ['Wartelisten'], at: '/station/members/list'},
    {
        key: 'QUIZ',
        name: 'quiz',
        entries: ['Fragenkataloge', 'Tests', 'Training'],
        at: '/station/quiz/tests',
    },
    {
        key: 'TEST_PROTOCOL',
        name: 'test protocols',
        entries: ['Prüfungsbögen', 'Prüfungsläufe'],
        at: '/station/protocols',
    },
]

/**
 * Flips one module and waits for the setting to have been written, which the switch reports itself:
 * it is bound to what came back from the server, not to what was clicked.
 */
async function setModule(page: Page, key: string, enabled: boolean): Promise<void> {
    await page.goto('/station/manage/modules')

    const row = page.locator(`[data-testid="module-toggle"][data-module="${key}"]`)
    const toggle = row.getByRole('switch')
    await expect(toggle).toHaveAttribute('aria-checked', String(!enabled))

    await toggle.click()
    await expect(toggle).toHaveAttribute('aria-checked', String(enabled))
}

test.describe('Station modules', () => {
    for (const mod of MODULES) {
        test(`switching the ${mod.name} module off removes it from the sidebar`, async ({partnerManagerPage: page}) => {
            const entries = mod.entries.map(entry => sidebarEntry(page, entry))

            await page.goto(mod.at)
            for (const entry of entries) await expect(entry).toBeVisible()

            await setModule(page, mod.key, false)

            await page.goto(mod.at)
            await expect(sidebar(page)).toBeVisible()
            for (const entry of entries) await expect(entry).toHaveCount(0)

            await setModule(page, mod.key, true)

            await page.goto(mod.at)
            for (const entry of entries) await expect(entry).toBeVisible()
        })
    }

    /**
     * Quiz and tests share one group, so the group is named after whichever of them the station
     * still runs. A station doing tests and no quiz reading "Quiz" in its sidebar would be naming
     * something it switched off.
     */
    test('the shared quiz group is named after the modules the station keeps', async ({partnerManagerPage: page}) => {
        await page.goto('/station/quiz/tests')
        await expect(sidebarEntry(page, 'Quiz & Prüfungen')).toBeVisible()

        await setModule(page, 'TEST_PROTOCOL', false)
        await page.goto('/station/quiz/tests')
        await expect(sidebarEntry(page, 'Quiz')).toBeVisible()
        await expect(sidebarEntry(page, 'Quiz & Prüfungen')).toHaveCount(0)

        await setModule(page, 'TEST_PROTOCOL', true)
        await setModule(page, 'QUIZ', false)
        await page.goto('/station/protocols')
        await expect(sidebarEntry(page, 'Prüfungen')).toBeVisible()

        await setModule(page, 'QUIZ', true)
        await page.goto('/station/quiz/tests')
        await expect(sidebarEntry(page, 'Quiz & Prüfungen')).toBeVisible()
    })
})
