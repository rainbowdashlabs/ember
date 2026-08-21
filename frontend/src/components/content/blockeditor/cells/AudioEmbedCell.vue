/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {AudioEmbedConfig} from '@/api/pageManage'

defineProps<{
    config: AudioEmbedConfig
}>()
</script>

<template>
    <div v-if="config.url" class="rounded-theme border border-(--border) p-3 space-y-2">
        <p v-if="config.title" class="font-medium text-sm">{{ config.title }}</p>
        <audio :src="config.url" controls class="w-full"/>
    </div>
    <p v-else class="text-sm text-(--text-muted) italic">Audio-URL fehlt</p>
</template>
