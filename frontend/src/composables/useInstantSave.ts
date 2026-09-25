/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onBeforeUnmount, ref, watch} from 'vue'

/**
 * Writes a setting back as soon as it is changed, rather than holding it until somebody presses a
 * button.
 *
 * <p>For the settings where forgetting to press it does damage: a form switched to reach fewer
 * people, a date moved. The reader sees the switch move and has every reason to believe that is
 * the end of it, so that had better be true.
 *
 * <p>Changes are held for a moment first, so typing a title is one write rather than one per
 * letter, and whatever is still held is written before the page goes away.
 */
export function useInstantSave<T>(source: () => T, save: (value: T) => Promise<void>, delayMs = 600) {
    const saving = ref(false)
    const failed = ref(false)
    let armed = false
    let pending: T | null = null
    let timer: ReturnType<typeof setTimeout> | null = null

    watch(
        source,
        value => {
            if (!armed) return
            pending = value
            if (timer) clearTimeout(timer)
            timer = setTimeout(() => void flush(), delayMs)
        },
        {deep: true},
    )

    async function flush() {
        if (timer) {
            clearTimeout(timer)
            timer = null
        }
        if (pending === null) return
        const value = pending
        pending = null
        saving.value = true
        try {
            await save(value)
            failed.value = false
        } catch {
            failed.value = true
        } finally {
            saving.value = false
        }
    }

    /**
     * Starts watching. Called once what was stored has been read into the fields, so that filling
     * them in does not count as a change and write straight back what was just read.
     */
    function arm() {
        armed = true
    }

    onBeforeUnmount(() => void flush())

    return {saving, failed, arm, flush}
}
