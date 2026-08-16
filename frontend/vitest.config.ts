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
        globals: true,
        include: ['src/**/*.{test,spec}.ts'],
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
