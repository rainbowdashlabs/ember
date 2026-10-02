/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { readonly, computed } from 'vue'

function isPrideMonth(): boolean {
    const month = new Date().getMonth() + 1
    return month === 6 || month === 7
}

/**
 * Whether the pride flag shows, and how.
 *
 * <p>The month is asked for on every call rather than once, so a server that runs from July into
 * August stops showing the flag. Whether the instance forces it on is held per request.
 *
 * <p>The flag looks the same however it was switched on. Reading the month here instead of whether
 * the flag is showing gave an instance that turned it on itself the gradient clipped into the
 * letters, and only June and July the flag behind them, so the setting appeared to do something
 * other than what it says.
 */
export function usePride() {
    const forcePrideFlag = useState('usePride.forced', () => false)
    const prideMonth = isPrideMonth()
    const prideActive = computed(() => prideMonth || forcePrideFlag.value)
    const prideVariant = computed((): 'text' | 'banner' => prideActive.value ? 'banner' : 'text')
    return {
        prideActive,
        prideVariant,
        forcePrideFlag: readonly(forcePrideFlag),
        setForcePrideFlag(value: boolean) { forcePrideFlag.value = value },
    }
}
