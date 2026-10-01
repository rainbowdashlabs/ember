/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {expect, test} from './fixtures/auth'

/**
 * The headers a rendered page carries, and whether the content security policy it declares is one
 * the application can actually live under.
 *
 * <p>The policy is enforced, so a violation is refused and reported on the console in the same words
 * either way. That is what makes it testable: a story that loads a page and finds no violation shows
 * the page lives under the policy rather than missing a piece the browser refused.
 */
function cspViolations(page: Page): string[] {
    const violations: string[] = []
    page.on('console', message => {
        if (/content security policy/i.test(message.text())) violations.push(message.text())
    })
    return violations
}

test.describe('Security headers', () => {
    test('a page declares a policy with a nonce for its inline scripts', async ({page}) => {
        const response = await page.goto('/')
        const headers = response?.headers() ?? {}

        const policy = headers['content-security-policy']
        expect(policy, 'the policy is enforced unless an operator steps back to reporting').toBeTruthy()
        expect(policy).toContain("object-src 'none'")
        expect(policy).toContain("frame-ancestors 'self'")
        expect(headers['x-frame-options']).toBe('SAMEORIGIN')
        expect(headers['x-content-type-options']).toBe('nosniff')

        const nonce = policy!.match(/'nonce-([^']+)'/)?.[1]
        expect(nonce, 'the policy names a nonce').toBeTruthy()

        // The served HTML rather than the DOM: a browser blanks the nonce attribute once it has
        // parsed the page, so that a selector cannot read it back out and hand it to an injection.
        const tags = (await response!.text()).split('<script').slice(1)
        expect(tags.length, 'the page carries scripts at all').toBeGreaterThan(0)
        for (const tag of tags) {
            expect(tag.slice(0, tag.indexOf('>')), 'every script carries the nonce of its response').toContain(
                `nonce="${nonce}"`,
            )
        }
    })

    test('two responses do not share a nonce', async ({page}) => {
        const first = await page.goto('/')
        const second = await page.goto('/login')

        const nonceOf = (policy: string | undefined) => policy?.match(/'nonce-([^']+)'/)?.[1]
        const firstNonce = nonceOf(first?.headers()['content-security-policy'])
        expect(firstNonce, 'the first response names a nonce').toBeTruthy()
        expect(firstNonce).not.toBe(nonceOf(second?.headers()['content-security-policy']))
    })

    test('the public pages raise no violation under the policy', async ({page}) => {
        const violations = cspViolations(page)

        await page.goto('/')
        await page.goto('/login')
        await page.goto('/discovery')
        await page.waitForLoadState('networkidle')

        expect(violations, violations.join('\n')).toEqual([])
    })

    /**
     * The pages behind a login are where the heavier machinery sits: the map, the editor and the
     * document viewers. They are the ones that decide whether the policy can be enforced.
     */
    test('the station pages raise no violation under the policy', async ({managerPage: page}) => {
        const violations = cspViolations(page)

        await page.goto('/station/dashboard/overview')
        await page.goto('/station/media')
        await page.goto('/station/knowledge')
        await page.waitForLoadState('networkidle')

        expect(violations, violations.join('\n')).toEqual([])
    })
})
