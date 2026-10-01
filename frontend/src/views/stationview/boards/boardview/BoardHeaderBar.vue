/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'

/**
 * The row above a board: its short key, the ticket search the caller puts into the default slot, the
 * create button and, for those who may manage boards, the way into the settings.
 */
defineProps<{
    shortKey: string
    showSettings: boolean
}>()

const emit = defineEmits<{
    create: []
    settings: []
}>()

const { t } = useI18n()
</script>

<template>
    <div class="flex items-center justify-between mb-4 flex-wrap gap-2">
        <div class="flex items-center gap-3">
            <span class="text-xs font-mono text-(--text-muted) bg-(--bg-accent) px-1.5 py-0.5 rounded">{{ shortKey }}</span>
        </div>
        <div class="flex items-center gap-2">
            <slot />
            <PrimaryButton @click="emit('create')">
                <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
                {{ t('boards.createTicket') }}
            </PrimaryButton>
            <IconButton
                v-if="showSettings"
                :icon="['fas', 'gears']"
                :label="t('boards.settings')"
                @click="emit('settings')"
            />
        </div>
    </div>
</template>
