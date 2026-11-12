/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly} from 'vue'
import type {MessageEvent as MessageSignal} from '@/api/generated/schema'
import {MESSAGE_EVENTS_URL} from '@/api/messages'
import {browserRef, browserShallowRef} from '@/util/browserState'

/** Something that wants to hear of every message signal. */
export type MessageListener = (signal: MessageSignal) => void

/** The one open stream of this tab, with everybody listening to it. */
interface Connection {
    source: EventSource | null
    listeners: Set<MessageListener>
    watchdog: ReturnType<typeof setTimeout> | null
    reopen: ReturnType<typeof setTimeout> | null
}

/**
 * How long the stream may stay silent before it counts as lost. The server pings every 20
 * seconds, so a minute without anything means a proxy holds the events back or the line is gone.
 */
const SILENCE_MS = 60_000

/** How long to wait before opening a stream again that the server closed for good. */
const REOPEN_MS = 30_000

const connection = browserShallowRef<Connection | null>(null)
const live = browserRef(false)

function deliver(event: globalThis.MessageEvent<string>) {
    const current = connection.value
    if (!current) return
    let signal: MessageSignal
    try {
        signal = JSON.parse(event.data) as MessageSignal
    } catch {
        return
    }
    current.listeners.forEach(listener => listener(signal))
}

function heard() {
    const current = connection.value
    if (!current) return
    live.value = true
    if (current.watchdog) clearTimeout(current.watchdog)
    current.watchdog = setTimeout(lost, SILENCE_MS)
}

function lost() {
    live.value = false
    const current = connection.value
    if (!current) return
    current.source?.close()
    current.source = null
    current.reopen = setTimeout(openSource, REOPEN_MS)
}

/**
 * Opens the stream. The browser reconnects a broken one on its own; one the server closed for
 * good, such as after signing out, is opened again a little later.
 */
function openSource() {
    const current = connection.value
    if (!current) return
    current.reopen = null
    const source = new EventSource(MESSAGE_EVENTS_URL, {withCredentials: true})
    source.addEventListener('ready', heard)
    source.addEventListener('ping', heard)
    source.addEventListener('signal', event => {
        heard()
        deliver(event)
    })
    source.addEventListener('error', () => {
        live.value = false
        if (source.readyState === EventSource.CLOSED) lost()
    })
    current.source = source
}

function close() {
    const current = connection.value
    if (!current) return
    current.source?.close()
    if (current.watchdog) clearTimeout(current.watchdog)
    if (current.reopen) clearTimeout(current.reopen)
    connection.value = null
    live.value = false
}

/**
 * Listens to the tab's message stream, opening it for the first listener and closing it after the
 * last one leaves.
 *
 * @param listener called with every signal
 * @returns what stops listening
 */
export function listenToMessages(listener: MessageListener): () => void {
    if (!connection.value) {
        connection.value = {source: null, listeners: new Set(), watchdog: null, reopen: null}
        openSource()
    }
    connection.value.listeners.add(listener)
    return () => {
        const current = connection.value
        if (!current) return
        current.listeners.delete(listener)
        if (current.listeners.size === 0) close()
    }
}

/** Whether the stream gets through right now; while it does not, pages ask on a timer instead. */
export function messageStreamLive() {
    return readonly(live)
}
