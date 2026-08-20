/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import type {PageRow} from '@/api/pageManage'
import ContentRow from './ContentRow.vue'
import type {ContentRenderContext} from '@/util/contentContext'

/**
 * A whole container of blocks, in reading order. This is what a rich article renders as, and what
 * a public page is built from.
 */
defineProps<{
    rows: PageRow[]
    context: ContentRenderContext
}>()
</script>

<template>
    <div class="space-y-2">
        <ContentRow v-for="(row, ri) in rows" :key="row.id || ri" :row="row" :context="context"/>
    </div>
</template>
