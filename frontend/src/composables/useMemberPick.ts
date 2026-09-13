/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'

/**
 * A member menu that acts on whoever is chosen and then empties itself.
 *
 * <p>Several screens do not hold a choice at all: picking somebody adds them to a session, registers them
 * for an appointment or puts them on a list, and the menu is ready for the next person straight away. The
 * menu itself models a choice, so each of those would otherwise carry the same three lines that read the
 * value, act on it and clear it.
 */
function pickAndClear<T>(convert: (value: string) => T | null, onPick: (chosen: T) => void) {
    const picked = ref('')

    function take() {
        const value = picked.value
        picked.value = ''
        const chosen = convert(value)
        if (chosen != null) onPick(chosen)
    }

    return {picked, take}
}

/**
 * Acts on the member who was chosen, by their row id.
 *
 * @param onPick what to do with them
 */
export function useMemberPick(onPick: (memberId: number) => void) {
    return pickAndClear(value => Number(value) || null, onPick)
}

/**
 * The same, by their UUID, which is how the page editor names somebody so a stored choice survives a
 * station transfer.
 *
 * @param onPick what to do with them
 */
export function useMemberUidPick(onPick: (memberUid: string) => void) {
    return pickAndClear(value => value || null, onPick)
}
