/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {defineVitestConfig} from '@nuxt/test-utils/config'

/**
 * Nuxt owns the Vite config, so the test runner takes its environment from `@nuxt/test-utils`
 * rather than from a hand-written Vite config. That is what makes auto-imports, `#imports` and the
 * `~`/`@` aliases resolve in a test exactly as they do in the app.
 *
 * The Nuxt environment costs startup time. A test touching no Nuxt API opts out per file with a
 * `@vitest-environment happy-dom` docblock and runs an order of magnitude faster; the unit layer
 * does that throughout.
 */
export default defineVitestConfig({
    test: {
        environment: 'nuxt',
        environmentOptions: {
            nuxt: {domEnvironment: 'happy-dom'},
        },
        /**
         * Booting Nuxt is the `beforeAll` of every file that has not opted out, and on a loaded
         * machine it does not fit in the ten seconds a hook is given by default. What that looked
         * like was a different handful of suites failing on each run, all of them saying the same
         * thing and all of them passing when run alone.
         */
        hookTimeout: 30_000,
        globals: true,
        /**
         * The linters under `scripts/` are reached as well as the application.
         *
         * A linter's value is what it catches and what it leaves alone, and neither can be read off
         * the regular expressions it is made of. The rule about rows that open a page decides what
         * a sweep of sixty-odd files has to change, so it is worth holding to a test of its own.
         */
        include: ['src/**/*.{test,spec}.ts', 'scripts/**/*.{test,spec}.ts'],
        exclude: ['e2e/**', 'node_modules/**', '.nuxt/**'],
        setupFiles: ['src/test/setup.ts'],
        coverage: {
            provider: 'v8',
            reporter: ['text', 'lcov', 'html'],
            include: ['src/**/*.{ts,vue}'],
            exclude: [
                'src/**/*.{test,spec}.ts',
                'src/test/**',
                'src/i18n/**',
                'src/pages/**',
            ],
            /**
             * The gate stands at what the suite reaches today, not at where it should end up.
             * Raise it after each batch of tests; it never goes down. Chasing a round number
             * before the tests exist buys assertions written to satisfy an arithmetic.
             */
            thresholds: {
                lines: 1.2,
                statements: 0.9,
                branches: 0.3,
                functions: 0.1,
            },
        },
    },
})
