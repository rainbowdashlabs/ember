/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, beforeEach, describe, expect, it} from 'vitest'
import {AxiosError, type AxiosResponse, type InternalAxiosRequestConfig} from 'axios'
import client from './client'
import {acceptStorage, getItem, setItem} from './storage'

/**
 * What the request client does with a stale session that the server refuses.
 *
 * <p>The token goes either way. Only a reader on a page that needs a session is sent to the login;
 * a reader of a public page stays where they are, since the page never needed the token.
 *
 * @vitest-environment happy-dom
 */
describe('a refused session', () => {
    const originalLocation = window.location
    const originalAdapter = client.defaults.adapter
    let location: {pathname: string, search: string, href: string}

    function openAt(pathname: string) {
        location = {pathname, search: '?tab=1', href: `http://localhost${pathname}?tab=1`}
        Object.defineProperty(window, 'location', {value: location, configurable: true})
    }

    async function requestRefused() {
        client.defaults.adapter = (config: InternalAxiosRequestConfig) => {
            const response = {status: 401, statusText: 'Unauthorized', data: {}, headers: {}, config} as AxiosResponse
            return Promise.reject(new AxiosError('refused', 'ERR_BAD_REQUEST', config, null, response))
        }
        await expect(client.get('/session')).rejects.toBeInstanceOf(AxiosError)
    }

    beforeEach(() => {
        localStorage.clear()
        acceptStorage(undefined, [])
        setItem('session_token', 'stale')
    })

    afterEach(() => {
        client.defaults.adapter = originalAdapter
        Object.defineProperty(window, 'location', {value: originalLocation, configurable: true})
    })

    it.each(['/discovery', '/public/station/musterstadt/blog', '/f/token', '/s/token', '/helpcenter'])(
        'leaves a reader of %s on the page',
        async (path) => {
            openAt(path)

            await requestRefused()

            expect(location.href).toBe(`http://localhost${path}?tab=1`)
            expect(getItem('session_token')).toBeNull()
        },
    )

    it('sends a reader of a signed-in page to the login, with the way back', async () => {
        openAt('/cluster/members')

        await requestRefused()

        expect(location.href).toBe(`/login?redirect=${encodeURIComponent('/cluster/members?tab=1')}`)
    })
})
