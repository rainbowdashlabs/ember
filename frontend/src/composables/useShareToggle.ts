/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {describeFailure, type Failure} from '@/util/failure'

/** Where the shares of one kind of entry live: which are shared, and switching one. */
export interface SharePort {
    listShared(): Promise<number[]>
    setShared(id: number, shared: boolean): Promise<void>
}

/**
 * Which entries of a list the station's federation partners may see, and switching that per entry.
 *
 * <p>An entry is shared with every partner or with none. Only readers allowed to share load or change
 * this; for everyone else nothing is shared as far as the list shows.
 *
 * @param port     where the shares live
 * @param canShare whether the reader may share
 * @param failure  the view's failure channel
 */
export function useShareToggle(port: SharePort, canShare: Ref<boolean>, failure: Ref<Failure | null>) {
    const {t} = useI18n()
    const shared = ref(new Set<number>())
    const busy = ref(new Set<number>())

    async function readBack() {
        shared.value = new Set(await port.listShared())
    }

    async function load() {
        if (!canShare.value) return
        try {
            await readBack()
        } catch (e) {
            failure.value = describeFailure(e, t)
        }
    }

    function isShared(id: number): boolean {
        return shared.value.has(id)
    }

    function isBusy(id: number): boolean {
        return busy.value.has(id)
    }

    /** Shares the entry with every partner, or stops sharing it where it is shared. */
    async function toggle(id: number) {
        if (isBusy(id)) return
        failure.value = null
        busy.value = new Set(busy.value).add(id)
        try {
            await port.setShared(id, !isShared(id))
            await readBack()
        } catch (e) {
            failure.value = describeFailure(e, t)
        } finally {
            const rest = new Set(busy.value)
            rest.delete(id)
            busy.value = rest
        }
    }

    return {load, isShared, isBusy, toggle}
}
