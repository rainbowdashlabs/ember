/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {defineComponent, h, nextTick, ref} from 'vue'
import {mount} from '@vue/test-utils'
import {describe, expect, it, vi} from 'vitest'
import {useMessageStream} from './useMessageStream'

const stop = vi.fn()
const listenToMessages = vi.fn(() => stop)

vi.mock('@/util/messageStreamState', () => ({
    listenToMessages: () => listenToMessages(),
    messageStreamLive: () => ref(false),
}))

/** A component that listens while the reader has messages, as the header's messages button does. */
function listenerWhile(available: {value: boolean}) {
    return mount(defineComponent(() => {
        useMessageStream(() => {}, 60_000, () => {}, () => available.value)
        return () => h('div')
    }))
}

/** The stream is listened to only while messages are available, and let go once they are not. */
describe('useMessageStream', () => {
    it('listens only while messages are available and lets go when they stop being so', async () => {
        const available = ref(false)
        const wrapper = listenerWhile(available)
        expect(listenToMessages).not.toHaveBeenCalled()

        available.value = true
        await nextTick()
        expect(listenToMessages).toHaveBeenCalledTimes(1)

        available.value = false
        await nextTick()
        expect(stop).toHaveBeenCalledTimes(1)

        wrapper.unmount()
        expect(stop).toHaveBeenCalledTimes(1)
    })
})
