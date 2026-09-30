/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Browser, BrowserContext} from '@playwright/test'

/**
 * Collects what the content security policy refused while a story ran.
 *
 * <p>The policy is enforced, so something it refuses is simply missing from the page: a script that
 * never ran, a request that never left. A story can pass over a gap like that without noticing, which
 * is why every refusal is written down here and fails the story at its end. The browser reports a
 * refusal only on the console, in the same words whether the policy is enforced or only reported.
 */
const VIOLATION = /Content Security Policy/i

/** Writes down every refusal the given context's pages report. */
export function listenForCspViolations(context: BrowserContext, violations: string[]): void {
    context.on('console', message => {
        if (VIOLATION.test(message.text())) violations.push(message.text())
    })
}

/**
 * Makes every context the story opens itself report its refusals too, for as long as the story runs.
 *
 * <p>Most stories open their pages through fixtures that call {@code browser.newContext} directly
 * rather than through the context Playwright hands out, so listening on that one alone would miss
 * nearly all of them.
 *
 * @return puts the browser back the way it was
 */
export function watchNewContexts(browser: Browser, violations: string[]): () => void {
    const original = browser.newContext.bind(browser)
    browser.newContext = async (...options: Parameters<Browser['newContext']>) => {
        const context = await original(...options)
        listenForCspViolations(context, violations)
        return context
    }
    return () => {
        browser.newContext = original
    }
}
