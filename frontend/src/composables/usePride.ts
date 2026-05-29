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
const prideVariant = computed((): 'text' | 'banner' => prideMonth.value ? 'banner' : 'text')

export function usePride() {
    return {
        prideActive: readonly(prideActive),
        prideVariant: readonly(prideVariant),
        forcePrideFlag: readonly(forcePrideFlag),
        setForcePrideFlag(value: boolean) { forcePrideFlag.value = value },
    }
}
