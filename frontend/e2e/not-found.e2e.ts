/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

/**
 * The page an address that leads nowhere ends on.
 *
 * It is worth a story of its own because of how it went missing. The page existed and was correct
 * the whole time; what disappeared was the one line that reached it, when the route registry it was
 * named in was replaced by the page tree. Nothing failed: no route is missing as far as a build is
 * concerned, so the linters, the type check and the production build all stayed green while every
 * unknown address quietly fell through to a bare page carrying none of the application's own
 * navigation. Only a visit says otherwise, which is what these stories are.
 */
test.describe('Unknown address', () => {
    /** An address no page tree of this application will ever match. */
    const nowhere = '/there-is-no-page-here'

    /**
     * NF-1 - An address that does not exist shows the application's own page.
     *
     * The status is asserted beside the panel because either alone would pass while the other is
     * broken: the bare page it fell through to also answered 404, and a page that renders the panel
     * with a 200 would tell a search engine the address is real.
     */
    test('an address that does not exist answers 404 with the error page', async ({page}) => {
        const response = await page.goto(nowhere)

        expect(response?.status(), 'the server calls the address missing').toBe(404)
        await expect(page.getByTestId('error-page')).toBeVisible()
        await expect(page.getByTestId('error-page')).toContainText('404')
        await expect(page.locator('header').first(), 'the page keeps its own navigation').toBeVisible()
        await expect(page.locator('footer').first()).toBeVisible()
    })

    /**
     * NF-2 - The way back off it works.
     *
     * The error state outlives a plain link: the address would change and the panel would stay,
     * which is the trap this asserts against rather than the click itself.
     */
    test('the way back leads off the error page', async ({page}) => {
        await page.goto(nowhere)
        await page.getByTestId('error-home').click()

        await expect(page).toHaveURL(/\/\?home$/)
        await expect(page.getByTestId('error-page')).toBeHidden()
    })

    /**
     * NF-3 - Signing in is offered only to somebody who is not.
     *
     * The server cannot read the browser's storage, so it renders this page knowing neither that
     * somebody is signed in nor that nobody is. Both halves are asserted because the interesting
     * failure is the offer standing in front of a member who is already signed in.
     */
    test('the error page offers signing in only to an anonymous visitor', async ({page}) => {
        await page.goto(nowhere)
        await expect(page.getByTestId('error-login')).toBeVisible()
    })

    test('the error page offers a signed-in member no way to sign in', async ({memberPage}) => {
        await memberPage.goto(nowhere)

        await expect(memberPage.getByTestId('error-page')).toBeVisible()
        await expect(memberPage.getByTestId('error-login')).toBeHidden()
    })
})
