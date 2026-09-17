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
 *
 * Every address below is a variable with the single-checkout value as its default, because the
 * stack is one per checkout: `toolchain.sh` derives a compose project and a block of ports from the
 * checkout path and exports them, so several checkouts can run the suite at once without dividing
 * the ports between them by hand. Run `npx playwright test` with none of them set and it is the
 * stack it always was, on 8899 and 3010.
 *
 * The Nuxt server's port is read back out of its own address rather than carried as a second
 * variable, so the two can never disagree about which port the run is on.
 */
const backendUrl = process.env.NUXT_BACKEND_URL || 'http://localhost:8899'

/**
 * Where the backend comes from. Set E2E_PREBUILT where `./gradlew installDist` has already run and
 * the stack should start that rather than compile the sources again inside its container.
 *
 * `--build` comes with it, because the distribution is baked into the image rather than mounted:
 * `up` on its own reuses whatever image is already there, which would silently run the build before
 * last. Nothing is cached on a fresh runner, so this costs it nothing.
 */
const prebuilt = !!process.env.E2E_PREBUILT
const composeFiles = prebuilt
    ? '-f ../docker/compose.dev.yaml -f ../docker/compose.e2e-prebuilt.yaml'
    : '-f ../docker/compose.dev.yaml'
const baseUrl = process.env.E2E_BASE_URL || 'http://localhost:3010'
const webPort = new URL(baseUrl).port || '3000'

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
    /**
     * Four against the built server it normally uses, two against a dev server, which compiles
     * each route on demand from a single process and falls behind under more.
     */
    workers: process.env.E2E_DEV_SERVER ? 2 : 4,
    fullyParallel: true,
    reporter: process.env.CI ? [['html', {outputFolder: 'e2e/report'}], ['list']] : 'list',

    use: {
        baseURL: baseUrl,
        /**
         * A bound on one action, which Playwright leaves off by default.
         *
         * <p>Without it a click whose target keeps moving out from under it is retried for as long
         * as there is time, never rejects, and takes the whole budget of the story with it. What
         * that leaves behind is a bare timeout on a page that looks perfectly healthy, with nothing
         * naming the action that hung: a passkey sign-in spent two minutes that way, and the catch
         * written to swallow exactly that race never ran, because a promise that never settles
         * cannot be caught.
         *
         * <p>Generous on purpose. It is here to end a hang, not to hurry a slow screen, and it sits
         * above every timeout a call passes for itself and below the story's own.
         */
        actionTimeout: 30_000,
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
     * A stack of its own, on ports nobody works on: the database and backend come from the `e2e`
     * compose profile and answer on 8899, and the Nuxt server runs from this checkout on 3010,
     * unless the environment names other ones.
     *
     * That separation is what lets the suite reset the database before every run. Pointed at the
     * dev stack it would delete whatever a developer had just set up.
     *
     * The frontend is the built server rather than a second dev server, for two reasons: Nuxt
     * allows only one dev server per project, so a suite that wanted its own would fight whoever
     * is working; and a dev server compiles each route the first time it is asked for, which the
     * suite outgrew. Set E2E_DEV_SERVER to use one anyway while writing a single story.
     */
    webServer: process.env.E2E_NO_SERVER
        ? undefined
        : [
            {
                // In the foreground, without -d: a command that returns straight away is taken for a
                // server that died, and the containers it started in the background go unnoticed -
                // the stack is still building the backend at that point. Staying attached also means
                // the stack goes down with the run that brought it up.
                //
                // The pull goes first and retries, because `up` fetches as its first act and dies
                // with the fetch: one reset connection to the registry failed an entire shard and
                // read like a broken test.
                command: `bash ../docker/e2e-pull.sh && docker compose ${composeFiles} --profile e2e up${prebuilt ? ' --build' : ''}`,
                url: `${backendUrl}/api/v1/public/config`,
                reuseExistingServer: true,
                // Without E2E_PREBUILT the backend is compiled inside its container from the sources
                // beside it, which on a cold machine is the whole build, so the wait is generous.
                timeout: 900_000,
            },
            !process.env.E2E_DEV_SERVER
                ? {
                    // Named in the command rather than handed over as an environment, which does
                    // not always reach the process: without the address the server falls back to
                    // its default backend and every proxied call answers 500.
                    command: `NUXT_BACKEND_URL=${backendUrl} NITRO_PORT=${webPort} node .output/server/index.mjs`,
                    url: baseUrl,
                    reuseExistingServer: !process.env.CI,
                    timeout: 120_000,
                }
                : {
                    command: `NUXT_BACKEND_URL=${backendUrl} npm run dev -- --port ${webPort}`,
                    url: baseUrl,
                    reuseExistingServer: !process.env.CI,
                    timeout: 120_000,
                },
        ],
})
