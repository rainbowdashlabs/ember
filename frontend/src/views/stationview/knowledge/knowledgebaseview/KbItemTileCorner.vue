/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import StationBadge from '@/components/badge/StationBadge.vue'
import type {KbItem} from './useKbItems'

/**
 * What a tile says about where an entry comes from, in the corner rather than in its face: the star
 * of a favourite and the name of the partner it belongs to. It keeps out of the room the marking
 * tick takes, and takes none at all where an entry is neither.
 */
defineProps<{
    item: KbItem
}>()
</script>

<template>
    <div
        v-if="item.favourite || item.stationName"
        class="absolute top-1 left-1 flex max-w-[calc(100%-2.5rem)] items-center gap-1 overflow-hidden"
    >
        <font-awesome-icon v-if="item.favourite" :icon="['fas', 'star']" class="text-xs text-yellow-500"/>
        <StationBadge v-if="item.stationName" :station-name="item.stationName"/>
    </div>
</template>
