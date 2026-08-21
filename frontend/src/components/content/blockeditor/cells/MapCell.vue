/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import type {MapConfig} from '@/api/pageManage'

const props = defineProps<{
    config: MapConfig
}>()

const mapUrl = computed(() => {
    const lat = props.config.latitude ?? 0
    const lon = props.config.longitude ?? 0
    const z = props.config.zoom ?? 14
    const span = 0.01 * Math.pow(2, 14 - z)
    return `https://www.openstreetmap.org/export/embed.html?bbox=${lon - span},${lat - span},${lon + span},${lat + span}&layer=mapnik&marker=${lat},${lon}`
})
</script>

<template>
    <div v-if="config.latitude != null && config.longitude != null" class="rounded-theme overflow-hidden border border-(--border)">
        <iframe :src="mapUrl" :style="{height: `${config.heightPx ?? 320}px`}" class="w-full block" loading="lazy"/>
        <p v-if="config.label" class="text-center text-xs text-(--text-muted) py-1">{{ config.label }}</p>
    </div>
    <EmptyHint v-else>Koordinaten fehlen</EmptyHint>
</template>
