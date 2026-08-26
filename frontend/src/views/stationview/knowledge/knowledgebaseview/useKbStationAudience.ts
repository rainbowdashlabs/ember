/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, watch, type Ref } from 'vue'
import { clusterGovernance, federation, knowledgeBase } from '@/api'
import type { KbEntryTarget } from './useKbEntryEditor'

/** A station an entry can be aimed at, and the partnership that addresses it. */
export interface AudienceStation {
    partnerId: number
    name: string
}

/** Who an entry leaves this wiki for: nobody, everyone it is connected to, or the ones named. */
export type AudienceMode = 'none' | 'all' | 'some'

/**
 * Which other stations one wiki entry is for.
 *
 * <p>An association's wiki shares everything it writes with all its stations the moment it is created, so
 * there its question is only how far to narrow that. A station's own wiki shares nothing until somebody
 * says so, so there "nobody" is a real answer and the one nearly every entry gives. Offering only
 * "everyone" and "these ones" would have shared every entry the moment its dialog was saved.
 *
 * @param show      whether the dialog is open; opening triggers the load
 * @param entry     the entry being shared
 * @param kind      whether the entry is an article or a folder
 * @param ofCluster whether this is an association's wiki rather than a station's own
 */
export function useKbStationAudience(
    show: Ref<boolean>,
    entry: () => KbEntryTarget | null,
    kind: 'files' | 'folders',
    ofCluster: boolean,
) {
    const stations = ref<AudienceStation[]>([])
    const chosen = ref<number[]>([])
    const mode = ref<AudienceMode>(ofCluster ? 'all' : 'none')
    const loaded = ref(false)

    watch(show, async (visible) => {
        const target = entry()
        if (!visible || !target) return
        stations.value = []
        chosen.value = []
        mode.value = ofCluster ? 'all' : 'none'
        loaded.value = false

        try {
            const partners = await federation.listPartners()
            stations.value = partners
                .filter(held => held.partner.status === 'ACTIVE')
                .map(held => ({partnerId: held.partner.id, name: held.partnerStationName}))

            const audiences = ofCluster
                ? await clusterGovernance.getWikiAudiences()
                : await knowledgeBase.getAudiences()
            const mine = audiences.find(audience => kind === 'files'
                ? audience.fileId === target.id
                : audience.folderId === target.id)
            if (mine) {
                mode.value = mine.scope === 'SPECIFIC' ? 'some' : 'all'
                chosen.value = mine.scope === 'SPECIFIC' ? [...mine.partnerIds] : []
            }
            loaded.value = true
        } catch {
            stations.value = []
        }
    })

    function toggle(partnerId: number) {
        chosen.value = chosen.value.includes(partnerId)
            ? chosen.value.filter(id => id !== partnerId)
            : [...chosen.value, partnerId]
    }

    /**
     * Written only once the current answer is known. Saving a dialog whose load failed would otherwise
     * write the default over whatever was really there.
     */
    async function save(): Promise<void> {
        const target = entry()
        if (!target || !loaded.value) return
        const payload = {
            [kind === 'files' ? 'fileId' : 'folderId']: target.id,
            everyStation: mode.value === 'all',
            partnerIds: mode.value === 'some' ? chosen.value : [],
        }
        if (ofCluster) await clusterGovernance.setWikiAudience(payload)
        else await knowledgeBase.setAudience({...payload, shared: mode.value !== 'none'})
    }

    return {stations, chosen, mode, save, toggle}
}
