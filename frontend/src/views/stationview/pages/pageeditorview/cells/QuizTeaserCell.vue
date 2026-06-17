/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import QuizTeaserRunner from '../QuizTeaserRunner.vue'
import type {QuizTeaserConfig} from '@/api/pageManage'

defineProps<{
    config: QuizTeaserConfig
    stationUid?: string | null
}>()
</script>

<template>
    <QuizTeaserRunner
        :title="config.title ?? null"
        :description="config.description ?? null"
        :catalog-ids="config.catalogIds ?? null"
        :station-uid="stationUid ?? null"
    />
</template>
