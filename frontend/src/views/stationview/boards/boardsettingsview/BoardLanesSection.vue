/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ColorInput from '@/components/input/ColorInput.vue'

export interface LaneDraft {
    name: string
    color: string | null
    id?: number
}

defineProps<{
    lanes: LaneDraft[]
    newLaneName: string
}>()

const emit = defineEmits<{
    (e: 'update:newLaneName', value: string): void
    (e: 'add'): void
    (e: 'remove', index: number): void
    (e: 'move', index: number, dir: -1 | 1): void
}>()

const { t } = useI18n()
</script>

<template>
    <NeutralContainer>
        <SubHeader class="text-sm mb-3">{{ t('boards.lanes') }}</SubHeader>
        <div class="space-y-2">
            <div v-for="(lane, index) in lanes" :key="index" class="flex items-center gap-2">
                <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-[var(--text-muted)] cursor-grab" />
                <ColorInput :model-value="lane.color ?? '#6b7280'" @update:model-value="lane.color = $event" />
                <TextInput v-model="lane.name" class="flex-1" />
                <IconButton :icon="['fas', 'chevron-up']" label="Move up" :disabled="index === 0" @click="emit('move', index, -1)" />
                <IconButton :icon="['fas', 'chevron-down']" label="Move down" :disabled="index === lanes.length - 1" @click="emit('move', index, 1)" />
                <IconButton :icon="['fas', 'xmark']" label="Remove" @click="emit('remove', index)" />
            </div>
        </div>
        <div class="flex gap-2 mt-3">
            <TextInput :model-value="newLaneName" :placeholder="t('boards.addLane')" class="flex-1" @update:model-value="v => emit('update:newLaneName', String(v))" @keydown.enter="emit('add')" />
            <SecondaryButton @click="emit('add')">
                <font-awesome-icon :icon="['fas', 'plus']" />
            </SecondaryButton>
        </div>
    </NeutralContainer>
</template>
