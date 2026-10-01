/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {AudioEmbedConfig} from '@/api/generated/schema'

defineProps<{
    config: AudioEmbedConfig
}>()

const {t} = useI18n()
</script>

<template>
    <div v-if="config.url" class="rounded-theme border border-(--border) p-3 space-y-2">
        <p v-if="config.title" class="font-medium text-sm">{{ config.title }}</p>
        <audio :src="config.url" controls class="w-full"/>
    </div>
    <p v-else class="text-sm text-(--text-muted) italic">{{ t('stationPages.cellHints.audioUrlMissing') }}</p>
</template>
