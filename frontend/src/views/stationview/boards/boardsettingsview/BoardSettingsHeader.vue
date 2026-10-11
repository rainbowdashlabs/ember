/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import IconButton from '@/components/button/IconButton.vue'
import SaveStatus from '@/components/feedback/SaveStatus.vue'

defineProps<{
    shortKey: string
    saving: boolean
    saved: boolean
}>()

const emit = defineEmits<{
    (e: 'back'): void
}>()

const { t } = useI18n()
</script>

<template>
    <div class="flex items-center justify-between mb-6">
        <div class="flex items-center gap-3">
            <IconButton :icon="['fas', 'chevron-left']" :label="t('common.back')" @click="emit('back')" />
            <SectionHeader>{{ t('boards.settings') }}</SectionHeader>
            <span class="text-xs font-mono text-[var(--text-muted)] bg-[var(--bg-accent)] px-1.5 py-0.5 rounded">{{ shortKey }}</span>
        </div>
        <SaveStatus :saving="saving" :saved="saved" />
    </div>
</template>
