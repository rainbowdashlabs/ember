/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {StatsCounterConfig} from '@/api/pageManage'

defineProps<{
    config: StatsCounterConfig
}>()
</script>

<template>
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <div v-for="(s, i) in config.items ?? []" :key="i"
             class="text-center rounded-theme border border-(--border) p-3">
            <div class="text-3xl font-bold text-primary tabular-nums">{{ s.value }}<span v-if="s.suffix" class="text-base font-normal">{{ s.suffix }}</span></div>
            <div class="text-xs text-(--text-muted) uppercase tracking-wider mt-1">{{ s.label }}</div>
        </div>
    </div>
</template>
