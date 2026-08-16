/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {defineConfig, devices} from '@playwright/test'

/**
 * The end-to-end suite. It walks the real application against a real backend and database, which is
 * why it is the only thing under `frontend/` allowed to reach above that directory: the backend it
 * needs is started from `docker/`. Nothing in the lint chain or the container image reads this file.
 *
 * The `ssr-no-js` project is not a nicety. Public routes are server-rendered by route rule, and a
 * context with JavaScript switched off is the only way to assert that they really are rather than
 * being repaired by hydration.
 */
export default defineConfig({
    testDir: './e2e',
    globalSetup: './e2e/global-setup.ts',
    outputDir: './e2e/results',
    // Specs are named after their feature, not `*.spec.ts`, so the default pattern would find none.
    testMatch: /.*\.e2e\.ts/,
    timeout: 60_000,
    /**
     * A dev server compiles each route the first time it is asked for, which can take longer than
     * an assertion is normally willing to wait. Running against a built server removes the wait
     * entirely; until the suite does that, the first visit to a page must not read as a failure.
     */
    expect: {timeout: 15_000},
    retries: process.env.CI ? 2 : 0,
    workers: process.env.CI ? 4 : undefined,
    fullyParallel: true,
    reporter: process.env.CI ? [['html', {outputFolder: 'e2e/report'}], ['list']] : 'list',

    use: {
        baseURL: process.env.E2E_BASE_URL || 'http://localhost:3000',
        trace: 'on-first-retry',
        screenshot: 'only-on-failure',
        video: 'on-first-retry',
    },

    projects: [
        {name: 'chromium', testIgnore: /.*\.ssr\.e2e\.ts/, use: {...devices['Desktop Chrome']}},
        {name: 'firefox', testIgnore: /.*\.ssr\.e2e\.ts/, use: {...devices['Desktop Firefox']}},
        {name: 'mobile', testIgnore: /.*\.ssr\.e2e\.ts/, use: {...devices['iPhone 14']}},
        {
            name: 'ssr-no-js',
            testMatch: /.*\.ssr\.e2e\.ts/,
            use: {...devices['Desktop Chrome'], javaScriptEnabled: false},
        },
    ],

    /**
     * Database and backend come from the dev stack, which publishes the backend on 8888; the Nuxt
     * server runs from this checkout so the suite tests the sources in front of it rather than a
     * container built from an older commit. The dev stack's own frontend service stays down, which
     * is what keeps port 3000 free for it.
     */
    webServer: process.env.E2E_NO_SERVER
        ? undefined
        : [
            {
                command: 'docker compose -f ../docker/compose.dev.yaml --profile full up -d postgres ember',
                url: 'http://localhost:8888/api/v1/public/config',
                reuseExistingServer: true,
                timeout: 300_000,
            },
            {
                command: 'npm run dev',
                url: 'http://localhost:3000',
                reuseExistingServer: !process.env.CI,
                timeout: 120_000,
                env: {NUXT_BACKEND_URL: process.env.NUXT_BACKEND_URL || 'http://localhost:8888'},
            },
        ],
})
