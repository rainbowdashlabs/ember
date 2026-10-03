/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import type {ContentRow as RowData} from '@/api/generated/schema'
import ContentCell from './ContentCell.vue'
import type {ContentRenderContext} from '@/util/contentContext'

/**
 * One row of blocks. Cells sit side by side at their configured widths and stack on a narrow
 * screen, where side by side stops being readable.
 */
defineProps<{
    row: RowData
    context: ContentRenderContext
}>()
</script>

<template>
    <div class="flex flex-row max-sm:flex-col">
        <div v-for="(cell, ci) in row.cells" :key="cell.id || ci"
             :style="{
                 flexGrow: 0,
                 flexShrink: 0,
                 flexBasis: `${cell.widthPercent ?? 100}%`,
             }"
             class="max-sm:!grow max-sm:!basis-full p-2 min-w-0"
             :class="row.columnLines && ci > 0 ? 'sm:border-l sm:border-(--border)' : ''">
            <ContentCell :cell="cell" :context="context"/>
        </div>
    </div>
</template>
