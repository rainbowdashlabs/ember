/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {watch} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {FieldType, type FieldTypeName} from '@/api/inventoryFields'
import type {InventoryItem} from '@/api/inventory'
import type {InventoryArt} from '@/api/inventoryArts'
import type {DraftField} from './types'
import {harmonizeKey} from './harmonize'
import FieldScopeSelect from './FieldScopeSelect.vue'

const props = withDefaults(
    defineProps<{
        draft: DraftField
        /** The kinds this inventory holds, empty where it holds one thing in many copies. */
        arts?: InventoryArt[]
        /** Its pieces, for a field that describes exactly one of them. */
        items?: InventoryItem[]
    }>(),
    {arts: () => [], items: () => []},
)

const emit = defineEmits<{
    (e: 'type-changed', value: FieldTypeName): void
}>()

const {t} = useI18n()

watch(() => props.draft.label, (label, previous) => {
    if (props.draft.id) return
    if (props.draft.key === '' || props.draft.key === harmonizeKey(previous ?? '')) {
        props.draft.key = harmonizeKey(label)
    }
})

function onTypeChanged(value: string | number | null | undefined) {
    emit('type-changed', String(value ?? '') as FieldTypeName)
}
</script>

<template>
    <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
        <label class="flex flex-col gap-1 text-sm">
            <span>{{ t('inventory.fields.label') }}</span>
            <TextInput v-model="props.draft.label" :placeholder="t('inventory.fields.labelPlaceholder')" />
        </label>
        <label class="flex flex-col gap-1 text-sm">
            <span>{{ t('inventory.fields.key') }}</span>
            <TextInput v-model="props.draft.key" :disabled="!!props.draft.id" :placeholder="t('inventory.fields.keyPlaceholder')" />
        </label>
        <label class="flex flex-col gap-1 text-sm">
            <span>{{ t('inventory.fields.type') }}</span>
            <SelectInput :model-value="props.draft.fieldType" :disabled="!!props.draft.id" @update:model-value="onTypeChanged">
                <option v-for="v in Object.values(FieldType)" :key="v" :value="v">{{ t(`inventory.fields.types.${v}`) }}</option>
            </SelectInput>
        </label>
        <label class="flex flex-col gap-1 text-sm">
            <span>{{ t('inventory.fields.sortOrder') }}</span>
            <NumberInput v-model="props.draft.sortOrder" />
        </label>
        <FieldScopeSelect :draft="props.draft" :arts="props.arts" :items="props.items" />
        <label class="flex items-center gap-2 text-sm md:col-span-2">
            <ToggleInput v-model="props.draft.required" />
            <span>{{ t('inventory.fields.required') }}</span>
        </label>
    </div>
</template>
