/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup generic="T extends Record<string, unknown>">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import MarkdownFieldInput from '@/components/input/text/MarkdownFieldInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import DragList from '@/components/input/DragList.vue'
import {moveWithin} from '@/util/reorder'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

export interface ItemFieldDef {
    key: string
    label: string
    type: 'text' | 'textarea' | 'markdown' | 'number'
    placeholder?: string
    span?: 1 | 2 | 3
}

const items = defineModel<T[] | undefined | null>('items')

const props = withDefaults(defineProps<{
    fields: ItemFieldDef[]
    addLabel?: string
    /** Columns in the per-item grid (defaults to the widest field span sum, capped at 2). */
    gridCols?: 1 | 2 | 3
}>(), {
    gridCols: 2,
})

const {t} = useI18n()

const safeItems = computed<T[]>(() => Array.isArray(items.value) ? items.value : [])

function setField(itemIndex: number, key: string, value: unknown) {
    items.value = safeItems.value.map((it, i) => i === itemIndex ? {...it, [key]: value} : it)
}

function addItem() {
    const empty = Object.fromEntries(props.fields.map(f => [f.key, f.type === 'number' ? null : ''])) as T
    items.value = [...safeItems.value, empty]
}

function removeItem(index: number) {
    items.value = safeItems.value.filter((_, i) => i !== index)
}

function moveItem(fromIndex: number, toIndex: number) {
    items.value = moveWithin(safeItems.value, fromIndex, toIndex)
}

const gridClass = computed(() => `grid-cols-1 sm:grid-cols-${props.gridCols}`)
</script>

<template>
    <div class="space-y-3">
        <DragList :items="safeItems" :key-fn="(_, index) => index" class="space-y-3" @reorder="moveItem">
        <template #default="{item, index: i}">
        <div class="rounded-theme border border-(--border) p-3 space-y-2">
            <div class="flex items-center justify-between">
                <span class="text-xs text-(--text-muted)">#{{ i + 1 }}</span>
                <IconButton :icon="['fas', 'trash']" :label="t('common.delete')" class="text-error" @click="removeItem(i)"/>
            </div>
            <div :class="['grid gap-2', gridClass]">
                <div v-for="f in fields" :key="f.key" :class="f.span ? `sm:col-span-${f.span}` : ''">
                    <FieldLabel hint class="mb-1">{{ f.label }}</FieldLabel>
                    <TextInput
                        v-if="f.type === 'text'"
                        :model-value="(item[f.key] as string) ?? ''"
                        :placeholder="f.placeholder"
                        @update:model-value="setField(i, f.key, $event)"
                    />
                    <TextAreaInput
                        v-else-if="f.type === 'textarea'"
                        :model-value="(item[f.key] as string) ?? ''"
                        :placeholder="f.placeholder"
                        :rows="3"
                        @update:model-value="setField(i, f.key, $event ?? '')"
                    />
                    <MarkdownFieldInput
                        v-else-if="f.type === 'markdown'"
                        :model-value="(item[f.key] as string) ?? ''"
                        :placeholder="f.placeholder"
                        @update:model-value="setField(i, f.key, $event ?? '')"
                    />
                    <NumberInput
                        v-else-if="f.type === 'number'"
                        :model-value="(item[f.key] as number) ?? undefined"
                        :placeholder="f.placeholder"
                        @update:model-value="setField(i, f.key, $event ?? null)"
                    />
                </div>
            </div>
        </div>
        </template>
        </DragList>
        <SecondaryButton :icon="['fas', 'plus']" @click="addItem">
            {{ addLabel ?? t('common.add') }}
        </SecondaryButton>
    </div>
</template>
