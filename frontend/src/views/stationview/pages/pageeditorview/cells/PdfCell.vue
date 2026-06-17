/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import EmptyHint from '@/components/typography/EmptyHint.vue'
import type {PdfConfig} from '@/api/pageManage'

defineProps<{
    config: PdfConfig
}>()
</script>

<template>
    <div v-if="config.url" class="rounded-theme overflow-hidden border border-(--border)">
        <iframe :src="config.url" :style="{height: `${config.heightPx ?? 600}px`}" class="w-full block" loading="lazy"/>
    </div>
    <EmptyHint v-else>PDF-URL fehlt</EmptyHint>
</template>
