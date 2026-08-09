/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type { BoardField, BoardFieldTypeName } from '@/api/boards'
import type { MemberCompletion } from '@/api/stationMembers'

const props = defineProps<{
    fields: BoardField[]
    members: MemberCompletion[]
    canEdit: boolean
}>()

const fieldValues = defineModel<Record<number, unknown>>('fieldValues', { default: () => ({}) })

const emit = defineEmits<{
    save: [fieldId: number, fieldType: BoardFieldTypeName, value: unknown]
}>()

const { t } = useI18n()
</script>

<template>
    <div v-if="props.fields.length > 0" class="border-t border-[var(--border)] pt-4"></div>
    <div v-for="field in props.fields" :key="field.id">
        <FieldLabel class="mb-1">{{ field.name }}</FieldLabel>
        <template v-if="canEdit">
            <TextInput v-if="field.fieldType === 'STRING'" :model-value="(fieldValues[field.id] as string) ?? ''" @blur="(e: Event) => emit('save', field.id, 'STRING', (e.target as HTMLInputElement).value || null)" />
            <NumberInput v-else-if="field.fieldType === 'NUMBER'" :model-value="(fieldValues[field.id] as number) ?? 0" @blur="(e: Event) => emit('save', field.id, 'NUMBER', Number((e.target as HTMLInputElement).value) || null)" />
            <CheckboxInput v-else-if="field.fieldType === 'BOOLEAN'" :model-value="!!fieldValues[field.id]" @update:model-value="(v: boolean) => emit('save', field.id, 'BOOLEAN', v)" />
            <SelectInput v-else-if="field.fieldType === 'ENUM'" class="w-full" :model-value="(fieldValues[field.id] as string) ?? ''" @update:model-value="v => emit('save', field.id, 'ENUM', v || null)">
                <option value="">—</option>
                <option v-for="opt in (field.config?.options ?? [])" :key="opt" :value="opt">{{ opt }}</option>
            </SelectInput>
            <DateInput v-else-if="field.fieldType === 'DATE'" :model-value="(fieldValues[field.id] as string) ?? ''" @change="(e: Event) => emit('save', field.id, 'DATE', (e.target as HTMLInputElement).value || null)" />
            <MemberSelectInput v-else-if="field.fieldType === 'LANE_ASSIGNEE'" :model-value="String(fieldValues[field.id] ?? '')" :members="members" :placeholder="t('boards.unassigned')" @change="emit('save', field.id, 'LANE_ASSIGNEE', Number(fieldValues[field.id]) || null)" @update:model-value="v => { fieldValues[field.id] = v ? Number(v) : null; emit('save', field.id, 'LANE_ASSIGNEE', v ? Number(v) : null) }" />
        </template>
        <div v-else class="text-sm px-2 py-1">{{ fieldValues[field.id] ?? '—' }}</div>
    </div>
</template>
