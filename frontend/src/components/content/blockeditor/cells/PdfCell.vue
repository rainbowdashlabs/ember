/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import type {PdfConfig} from '@/api/generated/schema'

defineProps<{
    config: PdfConfig
}>()

const {t} = useI18n()
</script>

<template>
    <div v-if="config.url" class="rounded-theme overflow-hidden border border-(--border)">
        <iframe :src="config.url" :title="t('stationPages.contentType.pdf')" :style="{height: `${config.heightPx ?? 600}px`}" class="w-full block" loading="lazy"/>
    </div>
    <EmptyHint v-else>{{ t('stationPages.cellHints.pdfUrlMissing') }}</EmptyHint>
</template>
