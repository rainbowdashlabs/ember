/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, readonly} from 'vue'
import {transfer} from '@/api'
import type {TransferStatus} from '@/api/transfer'

/** Whether the station has moved to another instance, asked once and read wherever it matters. */
export function useStationTransferStatus() {
    const status = useState<TransferStatus | null>('useStationTransferStatus.status', () => null)
    const loaded = useState('useStationTransferStatus.loaded', () => false)

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
