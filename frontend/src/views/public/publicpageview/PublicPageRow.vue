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
             :style="{ flex: `0 0 ${cell.widthPercent}%` }"
             class="max-sm:!flex-[1_1_100%] p-2">
            <PublicPageCell :cell="cell" :station-uid="stationUid" :page-title="pageTitle"/>
        </div>
    </div>
</template>
