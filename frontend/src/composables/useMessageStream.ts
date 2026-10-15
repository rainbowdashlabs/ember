/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onBeforeUnmount, onMounted} from 'vue'
import {listenToMessages, messageStreamLive, type MessageListener} from '@/util/messageStreamState'

/**
 * Hears the tab's message signals for as long as the component is mounted, and asks on a timer
 * instead while the stream does not get through.
 *
 * <p>The stream is one per tab: the first component listening opens it, the last one leaving closes
 * it. A proxy that holds events back leaves it silent, which is when the timer takes over.
 *
 * @param listener   called with every signal
 * @param pollEvery  how often to ask while the stream is silent, in milliseconds
 * @param poll       what asking means for this component
 */
export function useMessageStream(listener: MessageListener, pollEvery: number, poll: () => void) {
    const live = messageStreamLive()
    let stop: (() => void) | null = null
    let timer: ReturnType<typeof setInterval> | null = null

    onMounted(() => {
        stop = listenToMessages(listener)
        timer = setInterval(() => {
            if (!live.value) poll()
        }, pollEvery)
    })

    onBeforeUnmount(() => {
        stop?.()
        if (timer) clearInterval(timer)
    })

    return {live}
}
