/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {browserShallowRef} from '@/util/browserState'

/**
 * Whether the shift key is down, kept for whoever is asked afterwards.
 *
 * <p>A handler that is passed the thing it acts on rather than the event has no way of knowing, and
 * the alternative was threading the event through several dozen call sites that have no other use
 * for it. Written down in one place instead, from the pointer and from the keyboard alike, so a
 * button reached by tabbing to it answers the same as one that was clicked.
 */
const shift = browserShallowRef({held: false})

/**
 * Starts following the shift key, once, when the app comes up in the browser.
 *
 * <p>Leaving the window is the one way down without an up: the key is released where the page cannot
 * see it, and a shift believed to be held for ever would skip every question from then on. Losing the
 * focus therefore counts as letting go.
 */
export function followModifierKeys(): void {
    window.addEventListener('keydown', (e) => {
        if (e.key === 'Shift') shift.value.held = true
    }, true)
    window.addEventListener('keyup', (e) => {
        if (e.key === 'Shift') shift.value.held = false
    }, true)
    window.addEventListener('blur', () => {
        shift.value.held = false
    })
    window.addEventListener('mousedown', (e) => {
        shift.value.held = e.shiftKey
    }, true)
    window.addEventListener('click', (e) => {
        shift.value.held = e.shiftKey
    }, true)
}

/**
 * Whether the shift key was down as the current action was started.
 *
 * @return true while shift is held
 */
export function shiftIsHeld(): boolean {
    return shift.value.held
}
