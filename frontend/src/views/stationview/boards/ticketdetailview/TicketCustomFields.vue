/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import { fromCompletion } from '@/components/input/select/memberOption'
import type { BoardFieldRaw, BoardFieldTypeName, TypedBoardField } from '@/api/boards'
import type { MemberCompletion } from '@/api/generated/schema'

const props = defineProps<{
    fields: TypedBoardField[]
    members: MemberCompletion[]
    canEdit: boolean
}>()

const fieldValues = defineModel<Record<number, BoardFieldRaw | null>>('fieldValues', { default: () => ({}) })

const emit = defineEmits<{
    save: [fieldId: number, fieldType: BoardFieldTypeName, value: BoardFieldRaw | null]
}>()

const { t } = useI18n()

const assignable = computed(() => props.members.map(fromCompletion))

function textOf(fieldId: number): string {
    const value = fieldValues.value[fieldId]
    return typeof value === 'string' ? value : ''
}

function numberOf(fieldId: number): number {
    const value = fieldValues.value[fieldId]
    return typeof value === 'number' ? value : 0
}
</script>

<template>
    <div v-if="props.fields.length > 0" class="border-t border-[var(--border)] pt-4"/>
    <div v-for="field in props.fields" :key="field.id">
        <FieldLabel class="mb-1">{{ field.name }}</FieldLabel>
        <template v-if="canEdit">
            <TextInput v-if="field.fieldType === 'STRING'" :model-value="textOf(field.id)" @blur="(e: Event) => emit('save', field.id, 'STRING', (e.target as HTMLInputElement).value || null)" />
            <NumberInput v-else-if="field.fieldType === 'NUMBER'" :model-value="numberOf(field.id)" @blur="(e: Event) => emit('save', field.id, 'NUMBER', Number((e.target as HTMLInputElement).value) || null)" />
            <CheckboxInput v-else-if="field.fieldType === 'BOOLEAN'" :model-value="!!fieldValues[field.id]" @update:model-value="(v: boolean) => emit('save', field.id, 'BOOLEAN', v)" />
            <SelectInput v-else-if="field.fieldType === 'ENUM'" class="w-full" :model-value="textOf(field.id)" @update:model-value="v => emit('save', field.id, 'ENUM', v ? String(v) : null)">
                <option value="">-</option>
                <option v-for="opt in field.config.options" :key="opt" :value="opt">{{ opt }}</option>
            </SelectInput>
            <DateInput v-else-if="field.fieldType === 'DATE'" :model-value="textOf(field.id)" @change="(e: Event) => emit('save', field.id, 'DATE', (e.target as HTMLInputElement).value || null)" />
            <MemberSelectInput v-else-if="field.fieldType === 'LANE_ASSIGNEE'" :model-value="String(fieldValues[field.id] ?? '')" :members="assignable" :placeholder="t('boards.unassigned')" clearable @change="emit('save', field.id, 'LANE_ASSIGNEE', Number(fieldValues[field.id]) || null)" @update:model-value="v => { fieldValues[field.id] = v ? Number(v) : null; emit('save', field.id, 'LANE_ASSIGNEE', v ? Number(v) : null) }" />
        </template>
        <div v-else class="text-sm px-2 py-1">{{ fieldValues[field.id] ?? '-' }}</div>
    </div>
</template>
