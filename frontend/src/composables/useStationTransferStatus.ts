/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, readonly, ref} from 'vue'
import {transfer} from '@/api'
import type {TransferStatus} from '@/api/transfer'

const status = ref<TransferStatus | null>(null)
const loaded = ref(false)

export function useStationTransferStatus() {
    async function load() {
        try {
            status.value = await transfer.getTransferStatus()
        } catch {
            status.value = null
        }
        loaded.value = true
    }

    function reset() {
        status.value = null
        loaded.value = false
    }

    const hasMoved = computed(
        () => status.value?.readOnly === true && !!status.value?.targetInstanceUrl,
    )

    return {
        status: readonly(status),
        loaded: readonly(loaded),
        hasMoved,
        load,
        reset,
    }
}
