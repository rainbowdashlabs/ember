/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, beforeEach, describe, expect, it} from 'vitest'
import {AxiosError, type AxiosResponse, type InternalAxiosRequestConfig} from 'axios'
import client from './client'
import {acceptStorage, getItem, setItem} from './storage'

/** Sets or removes the readable cookie the server puts beside the session. */
function carrySession(token: string | null) {
    document.cookie = token === null ? 'ember_csrf=; max-age=0' : `ember_csrf=${token}`
}

/**
 * What the request client does with a stale session that the server refuses.
 *
 * <p>The station goes either way. Only a reader on a page that needs a session is sent to the login;
 * a reader of a public page stays where they are, since the page never needed the session.
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

    async function requestRefused(data: object = {}) {
        client.defaults.adapter = (config: InternalAxiosRequestConfig) => {
            const response = {status: 401, statusText: 'Unauthorized', data, headers: {}, config} as AxiosResponse
            return Promise.reject(new AxiosError('refused', 'ERR_BAD_REQUEST', config, null, response))
        }
        await expect(client.get('/session')).rejects.toBeInstanceOf(AxiosError)
    }

    beforeEach(() => {
        localStorage.clear()
        acceptStorage(undefined, [])
        setItem('station_id', 'stale-station')
        carrySession('stale')
    })

    afterEach(() => {
        client.defaults.adapter = originalAdapter
        Object.defineProperty(window, 'location', {value: originalLocation, configurable: true})
        carrySession(null)
    })

    it.each(['/discovery', '/public/station/musterstadt/blog', '/f/token', '/s/token', '/helpcenter'])(
        'leaves a reader of %s on the page',
        async (path) => {
            openAt(path)

            await requestRefused()

            expect(location.href).toBe(`http://localhost${path}?tab=1`)
            expect(getItem('station_id')).toBeNull()
        },
    )

    it('sends a reader of a signed-in page to the login, with the way back', async () => {
        openAt('/cluster/members')

        await requestRefused()

        expect(location.href).toBe(`/login?redirect=${encodeURIComponent('/cluster/members?tab=1')}`)
    })

    it('keeps a signed-in reader on the page when a named refusal says a confirmation was wrong', async () => {
        openAt('/station/members/groups')

        await requestRefused({code: 'STEP_UP_CODE_WRONG', message: 'Der Code stimmt nicht.'})

        expect(location.href).toBe('http://localhost/station/members/groups?tab=1')
        expect(getItem('station_id')).toBe('stale-station')
    })

    it('leaves a browser that carried no session where it is', async () => {
        carrySession(null)
        openAt('/cluster/members')

        await requestRefused()

        expect(location.href).toBe('http://localhost/cluster/members?tab=1')
        expect(getItem('station_id')).toBe('stale-station')
    })
})

/**
 * Every change carries the token from the readable cookie, so the server can tell it came from this
 * page; a read never needs it, and without a session there is nothing to send.
 *
 * @vitest-environment happy-dom
 */
describe('the request token', () => {
    const originalAdapter = client.defaults.adapter
    let sent: InternalAxiosRequestConfig | null = null

    beforeEach(() => {
        client.defaults.adapter = (config: InternalAxiosRequestConfig) => {
            sent = config
            return Promise.resolve({status: 200, statusText: 'OK', data: {}, headers: {}, config} as AxiosResponse)
        }
    })

    afterEach(() => {
        client.defaults.adapter = originalAdapter
        carrySession(null)
    })

    it.each(['post', 'put', 'patch', 'delete'] as const)('goes along with %s', async (method) => {
        carrySession('from-the-cookie')

        await client.request({url: '/anything', method})

        expect(sent?.headers['X-CSRF-Token']).toBe('from-the-cookie')
        expect(sent?.headers.Authorization).toBeUndefined()
    })

    it('stays home on a read', async () => {
        carrySession('from-the-cookie')

        await client.get('/anything')

        expect(sent?.headers['X-CSRF-Token']).toBeUndefined()
    })

    it('is not invented without a session', async () => {
        await client.post('/anything')

        expect(sent?.headers['X-CSRF-Token']).toBeUndefined()
    })
})
