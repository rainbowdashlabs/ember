/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {DividerConfig} from '@/api/generated/schema'

/**
 * A divider as the editor and the pages draw it: a horizontal line with its label, or a vertical line in
 * the middle of its column, as tall as the row it stands in.
 */
defineProps<{
    config: DividerConfig
}>()
</script>

<template>
    <div v-if="config.vertical" class="flex h-full min-h-16 justify-center" data-testid="divider-vertical-line">
        <div class="w-px bg-(--border)"/>
    </div>
    <div v-else class="flex items-center gap-3 py-2">
        <div class="flex-1 h-px bg-(--border)"/>
        <span v-if="config.label" class="text-xs uppercase tracking-wider text-(--text-muted)">{{ config.label }}</span>
        <div v-if="config.label" class="flex-1 h-px bg-(--border)"/>
    </div>
</template>
