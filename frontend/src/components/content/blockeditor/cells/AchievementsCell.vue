/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {AchievementsConfig} from '@/api/pageManage'

defineProps<{
    config: AchievementsConfig
}>()
</script>

<template>
    <div class="space-y-2">
        <p v-if="config.title" class="font-semibold">{{ config.title }}</p>
        <ul class="grid grid-cols-1 sm:grid-cols-2 gap-2">
            <li v-for="(a, i) in config.items ?? []" :key="i" class="rounded-theme border border-(--border) px-3 py-2 flex gap-2">
                <font-awesome-icon :icon="['fas', 'trophy']" class="text-yellow-500 text-xl mt-0.5"/>
                <div>
                    <p class="font-medium">{{ a.title }}</p>
                    <p v-if="a.year" class="text-xs text-primary">{{ a.year }}</p>
                    <p v-if="a.description" class="text-xs text-(--text-muted)">{{ a.description }}</p>
                </div>
            </li>
        </ul>
    </div>
</template>
