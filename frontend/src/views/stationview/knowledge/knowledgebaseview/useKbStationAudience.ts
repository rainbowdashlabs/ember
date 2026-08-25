/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, watch, type Ref } from 'vue'
import { clusterGovernance, federation } from '@/api'
import type { KbEntryTarget } from './useKbEntryEditor'

/** A station an entry can be aimed at, and the partnership that addresses it. */
export interface AudienceStation {
    partnerId: number
    name: string
}

/**
 * Which stations one entry of the association's wiki is for.
 *
 * <p>The association reaches its stations by sharing, and everything it writes is shared with all of them
 * the moment it is created. Naming stations narrows that to the ones named; naming none leaves it as it
 * is, which stays the default and is what every existing entry says.
 *
 * <p>Only the association's wiki asks this. A station's own wiki shares with federation partners, which is
 * a different conversation held on a different screen and behind a second factor.
 *
 * @param show    whether the dialog is open; opening triggers the load
 * @param entry   the entry being shared
 * @param enabled whether this wiki aims at stations at all
 * @param kind    whether the entry is an article or a folder
 */
export function useKbStationAudience(
    show: Ref<boolean>,
    entry: () => KbEntryTarget | null,
    enabled: boolean,
    kind: 'files' | 'folders',
) {
    const stations = ref<AudienceStation[]>([])
    const chosen = ref<number[]>([])
    const everyStation = ref(true)

    watch(show, async (visible) => {
        const target = entry()
        if (!visible || !target || !enabled) return
        stations.value = []
        chosen.value = []
        everyStation.value = true

        try {
            const partners = await federation.listPartners()
            stations.value = partners
                .filter(held => held.partner.status === 'ACTIVE')
                .map(held => ({partnerId: held.partner.id, name: held.partnerStationName}))

            const audiences = await clusterGovernance.getWikiAudiences()
            const mine = audiences.find(audience => kind === 'files'
                ? audience.fileId === target.id
                : audience.folderId === target.id)
            if (mine) {
                everyStation.value = mine.scope !== 'SPECIFIC'
                chosen.value = mine.scope === 'SPECIFIC' ? [...mine.partnerIds] : []
            }
        } catch {
            stations.value = []
        }
    })

    function toggle(partnerId: number) {
        chosen.value = chosen.value.includes(partnerId)
            ? chosen.value.filter(id => id !== partnerId)
            : [...chosen.value, partnerId]
    }

    async function save(): Promise<void> {
        const target = entry()
        if (!target || !enabled) return
        await clusterGovernance.setWikiAudience({
            [kind === 'files' ? 'fileId' : 'folderId']: target.id,
            everyStation: everyStation.value,
            partnerIds: everyStation.value ? [] : chosen.value,
        })
    }

    return {stations, chosen, everyStation, toggle, save}
}
