/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'

export interface DraftChecklistItem { key: number; title: string; checked: boolean }

defineProps<{
    items: DraftChecklistItem[]
    newTitle: string
}>()

const emit = defineEmits<{
    (e: 'update:newTitle', value: string): void
    (e: 'add'): void
    (e: 'remove', key: number): void
    (e: 'toggle', key: number): void
}>()

const { t } = useI18n()
</script>

<template>
    <NeutralContainer>
        <SubHeader class="mb-2">{{ t('boards.checklist') }}</SubHeader>
        <div v-for="item in items" :key="item.key" class="flex items-center gap-2 py-0.5">
            <CheckboxInput :model-value="item.checked" @update:model-value="emit('toggle', item.key)" />
            <span class="flex-1 text-sm" :class="{ 'line-through text-(--text-muted)': item.checked }">{{ item.title }}</span>
            <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-xs" @click="emit('remove', item.key)" />
        </div>
        <div class="flex gap-2 mt-2 items-center">
            <TextInput :model-value="newTitle" :placeholder="t('boards.addChecklistItem')" class="flex-1 text-sm" @update:model-value="v => emit('update:newTitle', String(v))" @keydown.enter="emit('add')" />
            <IconButton :icon="['fas', 'plus']" :label="t('common.add')" class="text-(--text-muted)" @click="emit('add')" />
        </div>
    </NeutralContainer>
</template>
