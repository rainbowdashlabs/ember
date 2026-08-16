/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import KbItemRow from './KbItemRow.vue'
import type {KbItem} from './useKbItems'

defineProps<{
    items: KbItem[]
}>()
</script>

<template>
    <div class="border border-[var(--border)] rounded-lg overflow-hidden divide-y divide-[var(--border)]">
        <KbItemRow v-for="item in items" :key="item.key" :item="item"/>
    </div>
</template>
