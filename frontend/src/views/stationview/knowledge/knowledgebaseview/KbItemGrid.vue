/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import KbItemTile from './KbItemTile.vue'
import type {KbItem} from './useKbItems'

defineProps<{
    items: KbItem[]
}>()
</script>

<template>
    <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
        <KbItemTile v-for="item in items" :key="item.key" :item="item"/>
    </div>
</template>
