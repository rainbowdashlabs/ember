/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, readonly, computed } from 'vue'

const forcePrideFlag = ref(false)

function isPrideMonth(): boolean {
    const month = new Date().getMonth() + 1
    return month === 6 || month === 7
}

const prideMonth = computed(() => isPrideMonth())
const prideActive = computed(() => prideMonth.value || forcePrideFlag.value)

/**
 * The flag looks the same however it was switched on. Reading the month here instead of whether
 * the flag is showing gave an instance that turned it on itself the gradient clipped into the
 * letters, and only June and July the flag behind them, so the setting appeared to do something
 * other than what it says.
 */
const prideVariant = computed((): 'text' | 'banner' => prideActive.value ? 'banner' : 'text')

export function usePride() {
    return {
        prideActive: readonly(prideActive),
        prideVariant: readonly(prideVariant),
        forcePrideFlag: readonly(forcePrideFlag),
        setForcePrideFlag(value: boolean) { forcePrideFlag.value = value },
    }
}
