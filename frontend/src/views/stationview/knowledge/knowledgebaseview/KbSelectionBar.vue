/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

/**
 * What can be done to the entries marked in the folder being browsed. Appears with the first mark
 * and goes with the last, so it never takes room from a reader who is only reading.
 */
defineProps<{
    selectedCount: number
}>()

const emit = defineEmits<{
    move: []
    tag: []
    clear: []
}>()

const {t} = useI18n()
</script>

<template>
    <NeutralContainer
        v-if="selectedCount > 0"
        class="mb-3 flex flex-wrap items-center gap-2 !py-2"
        data-testid="kb-selection-bar"
    >
        <span class="text-sm">{{ t('kb.selectedCount', {count: selectedCount}) }}</span>
        <SecondaryButton data-testid="kb-selection-move" @click="emit('move')">
            <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="mr-1"/>
            {{ t('kb.move') }}
        </SecondaryButton>
        <SecondaryButton data-testid="kb-selection-tag" @click="emit('tag')">
            <font-awesome-icon :icon="['fas', 'tag']" class="mr-1"/>
            {{ t('kb.tagSelected') }}
        </SecondaryButton>
        <SecondaryButton @click="emit('clear')">{{ t('kb.clearSelection') }}</SecondaryButton>
    </NeutralContainer>
</template>
