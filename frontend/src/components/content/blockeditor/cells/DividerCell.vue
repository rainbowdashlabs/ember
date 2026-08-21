/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {DividerConfig} from '@/api/pageManage'

defineProps<{
    config: DividerConfig
}>()
</script>

<template>
    <div class="flex items-center gap-3 py-2">
        <div class="flex-1 h-px bg-(--border)"/>
        <span v-if="config.label" class="text-xs uppercase tracking-wider text-(--text-muted)">{{ config.label }}</span>
        <div v-if="config.label" class="flex-1 h-px bg-(--border)"/>
    </div>
</template>
