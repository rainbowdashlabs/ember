/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import TicketFieldValue from './TicketFieldValue.vue'
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

const assignable = computed(() => props.members.map(fromCompletion))
</script>

<template>
    <div v-if="props.fields.length > 0" class="border-t border-[var(--border)] pt-4"/>
    <TicketFieldValue
        v-for="field in props.fields"
        :key="field.id"
        :can-edit="canEdit"
        :field="field"
        :members="assignable"
        :model-value="fieldValues[field.id] ?? null"
        @save="(type, value) => emit('save', field.id, type, value)"
        @update:model-value="value => fieldValues = {...fieldValues, [field.id]: value}"
    />
</template>
