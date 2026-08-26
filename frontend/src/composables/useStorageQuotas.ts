/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {inject, provide, ref, type InjectionKey, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {apiErrorMessage} from '@/util/apiError'
import type {CategoryUsage, QuotaOriginName} from '@/api/storageMonitoring'

/**
 * One station as the storage panels read it.
 *
 * <p>Named after what the panels use rather than after either owner, so the instance's own listing and an
 * association's picture of its stations both pass through without either pretending to be the other. An
 * instance's row satisfies this shape as it stands; an association's carries two things more.
 */
export interface StorageRoomRow {
    /** The station's identity, which is what every write is addressed to. */
    stationId: string
    stationName: string
    totalBytes: number
    quotaBytes: number
    quotaUsedPercent: number
    categories: CategoryUsage[]
    presetId: number | null
    presetName: string | null
    usesOwnBackend: boolean
    /** Whose word the quota is on, where the owner can say. */
    origin?: QuotaOriginName | null
    /** An association's own store rather than one of its member stations. */
    ownStore?: boolean
}

/** A reusable set of quotas, which both owners keep and hand out. */
export interface QuotaTier {
    id: number
    name: string
    total: number
    kb: number
    board: number
    images: number
    pages: number
    perFile: number
    perImage: number
}

export type QuotaTierValues = Omit<QuotaTier, 'id'>

/**
 * Where a set of quotas lives, and what its owner may do with one.
 *
 * <p>The instance keeps tiers for every station on it and can recount what a station is actually using. An
 * association keeps tiers for its own stations and cannot, because reconciling files is the instance's job.
 * The panels are the same panels; the difference sits here.
 */
export interface StorageQuotasPort {
    load(): Promise<{stations: StorageRoomRow[]; tiers: QuotaTier[]}>
    createTier(values: QuotaTierValues): Promise<unknown>
    updateTier(tierId: number, values: QuotaTierValues): Promise<unknown>
    deleteTier(tierId: number): Promise<unknown>
    applyTier(tierId: number, stationIds: string[]): Promise<unknown>
    /** Puts a station back on whatever stands behind: the instance's defaults, or the association's. */
    resetStation(stationId: string): Promise<unknown>
    /** Only the instance counts the bytes again, so only the instance offers it. */
    recalculateStation?: (stationId: string) => Promise<unknown>
}

/**
 * What the panels below may draw, which follows from who owns the quotas. Injected rather than handed down
 * through rows that have no use for it themselves.
 */
export interface StorageCapabilities {
    /** Whether a row offers to count its bytes again. */
    canRecalculate: boolean
    /** Whether a row says whose word its number is on. */
    showsOrigin: boolean
    /**
     * Whether a row an association governs is read-only here. It is for the instance, whose lever on such a
     * station is the pool it grants the association, and it is not for the association itself.
     */
    deferToCluster: boolean
}

const STORAGE_CAPABILITIES: InjectionKey<StorageCapabilities> = Symbol('storageCapabilities')

/** What the instance may draw, which is the answer for anything mounted outside these screens. */
const INSTANCE_CAPABILITIES: StorageCapabilities = {
    canRecalculate: true,
    showsOrigin: false,
    deferToCluster: true,
}

export function useStorageCapabilities(): StorageCapabilities {
    return inject(STORAGE_CAPABILITIES, INSTANCE_CAPABILITIES)
}

/**
 * The storage screens, without their markup.
 *
 * <p>Loading the stations and the tiers together, the tier writes, applying one to a selection, and putting a
 * station back on what stands behind it. The instance screen and the association screen pass different ports
 * and render the same panels.
 *
 * @param port         where the quotas live and what may be done with them
 * @param capabilities what the panels may draw
 */
export function useStorageQuotas(port: StorageQuotasPort, capabilities: StorageCapabilities) {
    const {t} = useI18n()

    provide(STORAGE_CAPABILITIES, capabilities)

    const stations: Ref<StorageRoomRow[]> = ref([])
    const tiers: Ref<QuotaTier[]> = ref([])
    const loading = ref(true)
    const busy = ref(false)
    const error = ref('')

    async function reload() {
        loading.value = true
        error.value = ''
        try {
            const loaded = await port.load()
            stations.value = loaded.stations
            tiers.value = loaded.tiers
        } catch (e) {
            error.value = apiErrorMessage(e) ?? t('common.error')
        } finally {
            loading.value = false
        }
    }

    /** Runs a write and reloads, so every panel sees the same picture again afterwards. */
    async function run(write: () => Promise<unknown>): Promise<boolean> {
        busy.value = true
        error.value = ''
        try {
            await write()
            await reload()
            return true
        } catch (e) {
            error.value = apiErrorMessage(e) ?? t('common.error')
            return false
        } finally {
            busy.value = false
        }
    }

    function saveTier(values: QuotaTierValues, tierId: number | null) {
        return run(() => (tierId === null ? port.createTier(values) : port.updateTier(tierId, values)))
    }

    function removeTier(tierId: number) {
        return run(() => port.deleteTier(tierId))
    }

    function applyTier(tierId: number, stationIds: string[]) {
        return run(() => port.applyTier(tierId, stationIds))
    }

    function resetStation(stationId: string) {
        return run(() => port.resetStation(stationId))
    }

    function recalculateStation(stationId: string) {
        if (!port.recalculateStation) return Promise.resolve(false)
        return run(() => port.recalculateStation!(stationId))
    }

    return {
        stations, tiers, loading, busy, error,
        reload, run, saveTier, removeTier, applyTier, resetStation, recalculateStation,
    }
}
