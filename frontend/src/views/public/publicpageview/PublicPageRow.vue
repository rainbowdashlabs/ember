/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import type {PageRow} from '@/api/pageManage'
import PublicPageCell from './PublicPageCell.vue'

defineProps<{
    row: PageRow
    stationUid: string
    pageTitle: string
}>()
</script>

<template>
    <div class="flex flex-row max-sm:flex-col">
        <div v-for="cell in row.cells" :key="cell.id"
             :style="{
                 flexGrow: 0,
                 flexShrink: 0,
                 flexBasis: `${cell.widthPercent ?? 100}%`,
             }"
             class="max-sm:!grow max-sm:!basis-full p-2 min-w-0">
            <PublicPageCell :cell="cell" :station-uid="stationUid" :page-title="pageTitle"/>
        </div>
    </div>
</template>
