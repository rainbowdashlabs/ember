/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import DragList from '@/components/input/DragList.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { BoardChecklistItem } from '@/api/boards'

const newTitle = defineModel<string>('newTitle', {required: true})

const props = defineProps<{
    checklist: BoardChecklistItem[]
    readonly?: boolean
}>()

const emit = defineEmits<{
    toggle: [item: BoardChecklistItem]
    add: []
    remove: [itemId: number]
    removeAll: []
    reorder: [fromIndex: number, toIndex: number]
}>()

const { t } = useI18n()
const checked = computed(() => props.checklist.filter(i => i.checked).length)
const total = computed(() => props.checklist.length)
</script>

<template>
    <NeutralContainer>
        <div class="flex items-center justify-between mb-2">
            <SubHeader>{{ t('boards.checklist') }} ({{ checked }}/{{ total }})</SubHeader>
            <IconButton v-if="!readonly" :icon="['fas', 'trash']" :label="t('common.delete')" class="text-xs text-(--text-muted)" @click="emit('removeAll')" />
        </div>
        <div v-if="total > 0" class="h-1.5 bg-[var(--bg-muted)] rounded-full mb-3 overflow-hidden">
            <div class="h-full rounded-full transition-all" :class="checked === total ? 'bg-green-500' : 'bg-primary'" :style="{ width: (checked / total * 100) + '%' }" />
        </div>
        <template v-if="readonly">
            <div v-for="item in checklist" :key="item.id" class="flex items-center gap-2 py-0.5">
                <CheckboxInput :model-value="item.checked" disabled />
                <span :class="{ 'line-through text-[var(--text-muted)]': item.checked }" class="flex-1 text-sm">{{ item.title }}</span>
            </div>
        </template>
        <template v-else>
            <DragList :items="checklist" :key-fn="(item: any) => item.id" @reorder="(f, t) => emit('reorder', f, t)">
                <template #default="{ item }">
                    <div class="flex items-center gap-2 group py-0.5">
                        <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-[var(--text-muted)] cursor-grab text-xs" />
                        <CheckboxInput :model-value="item.checked" @update:model-value="emit('toggle', item)" />
                        <span :class="{ 'line-through text-[var(--text-muted)]': item.checked }" class="flex-1 text-sm">{{ item.title }}</span>
                        <IconButton :icon="['fas', 'xmark']" label="Remove" class="sm:opacity-0 sm:group-hover:opacity-100 text-xs" @click="emit('remove', item.id)" />
                    </div>
                </template>
            </DragList>
            <div class="flex gap-2 mt-2 items-center">
                <TextInput v-model="newTitle" :placeholder="t('boards.addChecklistItem')" class="flex-1 text-sm" @keydown.enter="emit('add')" />
                <IconButton :icon="['fas', 'plus']" :label="t('common.add')" class="text-(--text-muted)" @click="emit('add')" />
            </div>
        </template>
    </NeutralContainer>
</template>
