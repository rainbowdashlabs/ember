/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import IconButton from '@/components/button/IconButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'

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
            <IconButton :icon="['fas', 'chevron-left']" label="Back" @click="emit('back')" />
            <SectionHeader>{{ t('boards.settings') }}</SectionHeader>
            <span class="text-xs font-mono text-[var(--text-muted)] bg-[var(--bg-muted)] px-1.5 py-0.5 rounded">{{ shortKey }}</span>
        </div>
        <span v-if="saving" class="text-xs text-[var(--text-muted)] flex items-center gap-1">
            <Spinner size="sm" /> {{ t('common.saving') }}
        </span>
        <span v-else-if="saved" class="text-xs text-success flex items-center gap-1">
            <font-awesome-icon :icon="['fas', 'check']" /> {{ t('common.saved') }}
        </span>
    </div>
</template>
