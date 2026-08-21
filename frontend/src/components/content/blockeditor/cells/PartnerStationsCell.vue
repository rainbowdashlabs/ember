/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import * as publicPages from '@/api/publicPages'
import type {PartnerStationsConfig} from '@/api/pageManage'

const props = defineProps<{
    config: PartnerStationsConfig
    stationUid?: string
}>()

const partners = ref<Array<{uid: string; name?: string; url?: string; distanceKm?: number}>>([])

async function resolve() {
    if (!props.stationUid) return
    const order = props.config.stationUids ?? []
    if (!props.config.autoFillFromPartners && order.length === 0) {
        partners.value = []
        return
    }
    try {
        const list = props.config.autoFillFromPartners
            ? await publicPages.listPartnerStations(props.stationUid)
            : await publicPages.resolvePartnerStations(props.stationUid, order)
        partners.value = list.map(p => ({
            uid: p.uid,
            name: p.name,
            url: p.slug ? `/public/station/${p.slug}` : `/public/station/${p.uid}`,
            distanceKm: p.distanceKm ?? undefined,
        }))
    } catch { partners.value = [] }
}

onMounted(resolve)
watch(
    () => [props.config.autoFillFromPartners, (props.config.stationUids ?? []).join(',')],
    resolve,
)
</script>

<template>
    <div class="space-y-2">
        <p v-if="config.title" class="font-semibold">{{ config.title }}</p>
        <ul class="grid grid-cols-1 sm:grid-cols-2 gap-2">
            <li v-for="(p, i) in partners" :key="i" class="rounded-theme border border-(--border) px-3 py-2">
                <a v-if="p.url" :href="p.url" target="_blank" rel="noopener noreferrer" class="font-medium hover:text-primary hover:underline">{{ p.name }}</a>
                <span v-else class="font-medium">{{ p.name }}</span>
                <p v-if="p.distanceKm != null" class="text-xs text-(--text-muted)">{{ p.distanceKm.toFixed(1) }} km entfernt</p>
            </li>
        </ul>
    </div>
</template>
