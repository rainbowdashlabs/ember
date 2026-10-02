/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import type {ProbeResult} from '@/api/generated/schema'
import {type StorageBackendApi, useStorageBackendEditor} from './useStorageBackendEditor'

vi.mock('vue-i18n', () => ({useI18n: () => ({t: (key: string) => key})}))

const ANSWERED: ProbeResult = {healthy: true, error: null, checkedAt: '2026-10-02T10:00:00Z'}

function fakeApi(overrides: Partial<StorageBackendApi> = {}): StorageBackendApi {
    return {
        probeSaved: vi.fn(async () => ANSWERED),
        probeTyped: vi.fn(async () => ANSWERED),
        reload: vi.fn(async () => undefined),
        ...overrides,
    }
}

describe('useStorageBackendEditor', () => {
    it('seeds the form from what is stored and starts at the fallback when nothing is', () => {
        const editor = useStorageBackendEditor(fakeApi(), 'LOCAL')

        editor.seed({
            type: 'SMB',
            host: 'nas',
            port: 445,
            share: 'ablage',
            domain: null,
            basePath: 'ember',
            seal: true,
            dfs: false,
            credentialFingerprint: 'abc',
        }, 'LOCAL')
        expect(editor.selectedType.value).toBe('SMB')
        expect(editor.currentRequest()).toMatchObject({type: 'SMB', share: 'ablage', domain: '', password: ''})

        editor.seed(null, 'S3')
        expect(editor.selectedType.value).toBe('S3')
    })

    it('describes the instance disk with its directory and an association with nothing more', () => {
        const editor = useStorageBackendEditor(fakeApi(), 'LOCAL')
        editor.seed({type: 'LOCAL', root: 'archiv'}, 'LOCAL')
        expect(editor.currentRequest()).toEqual({type: 'LOCAL', root: 'archiv'})

        editor.selectedType.value = 'CLUSTER'
        expect(editor.currentRequest()).toEqual({type: 'CLUSTER'})
    })

    it('tests the saved and the typed storage apart and shows a failed request as a failed test', async () => {
        const api = fakeApi({probeTyped: vi.fn(async () => Promise.reject(new Error('offline')))})
        const editor = useStorageBackendEditor(api, 'S3')

        await editor.probeSaved()
        await editor.probeTyped()

        expect(editor.savedOutcome.value).toEqual(ANSWERED)
        expect(editor.typedOutcome.value?.healthy).toBe(false)
        expect(api.probeTyped).toHaveBeenCalledWith(expect.objectContaining({type: 'S3'}))
    })

    it('holds a change that moves files until it is confirmed, then reads the screen again', async () => {
        const api = fakeApi()
        const editor = useStorageBackendEditor(api, 'LOCAL')
        const act = vi.fn(async () => 'moved')

        editor.askFirst('the files go', act)
        expect(editor.pending.value?.body).toBe('the files go')
        expect(act).not.toHaveBeenCalled()

        await editor.confirmPending()

        expect(act).toHaveBeenCalledTimes(1)
        expect(editor.pending.value).toBeNull()
        expect(editor.success.value).toBe('moved')
        expect(api.reload).toHaveBeenCalledTimes(1)
    })

    it('drops a change the reader cancels and runs one that moves nothing at once', async () => {
        const editor = useStorageBackendEditor(fakeApi(), 'LOCAL')
        const dropped = vi.fn(async () => 'never')

        editor.askFirst('the files go', dropped)
        editor.cancelPending()
        await editor.confirmPending()
        await editor.perform(async () => 'saved')

        expect(dropped).not.toHaveBeenCalled()
        expect(editor.success.value).toBe('saved')
    })

    it('keeps a failed change as a failure and leaves no success behind', async () => {
        const api = fakeApi()
        const editor = useStorageBackendEditor(api, 'LOCAL')

        await editor.perform(async () => Promise.reject(new Error('refused')))

        expect(editor.failure.value).not.toBeNull()
        expect(editor.success.value).toBe('')
        expect(api.reload).not.toHaveBeenCalled()
    })
})
