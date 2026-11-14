/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onBeforeUnmount, onMounted, watch} from 'vue'
import {listenToMessages, messageStreamLive, type MessageListener} from '@/util/messageStreamState'

/**
 * Hears the tab's message signals for as long as the component is mounted, and asks on a timer
 * instead while the stream does not get through.
 *
 * <p>The stream is one per tab: the first component listening opens it, the last one leaving closes
 * it. A proxy that holds events back leaves it silent, which is when the timer takes over. While
 * `active` says no, as when messages are not available to the reader, the component does not listen
 * and only the timer asks, so it notices when they become available.
 *
 * @param listener   called with every signal
 * @param pollEvery  how often to ask while the stream is silent, in milliseconds
 * @param poll       what asking means for this component
 * @param active     whether to listen right now
 */
export function useMessageStream(listener: MessageListener, pollEvery: number, poll: () => void, active: () => boolean = () => true) {
    const live = messageStreamLive()
    let stop: (() => void) | null = null
    let timer: ReturnType<typeof setInterval> | null = null
    let mounted = false

    function follow(on: boolean) {
        if (on && mounted && !stop) stop = listenToMessages(listener)
        if (!on && stop) {
            stop()
            stop = null
        }
    }

    watch(active, follow)

    onMounted(() => {
        mounted = true
        follow(active())
        timer = setInterval(() => {
            if (!live.value) poll()
        }, pollEvery)
    })

    onBeforeUnmount(() => {
        mounted = false
        follow(false)
        if (timer) clearInterval(timer)
    })

    return {live}
}
