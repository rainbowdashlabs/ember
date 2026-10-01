/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldAnswerInput from '@/components/input/FieldAnswerInput.vue'
import QuestionValueDisplay from '@/components/display/QuestionValueDisplay.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type { MemberOption } from '@/components/input/select/memberOption'
import { FieldTypes } from '@/api/fieldTypes'
import { fieldValueOfText, fieldValueText, type BoardFieldRaw, type BoardFieldTypeName, type TypedBoardField } from '@/api/boards'

/**
 * One custom field of a ticket, answered in the box every field is answered in and shown the way
 * every answer is shown.
 *
 * <p>A value a reader picks, a yes, a choice or a member, is saved the moment it is picked; one that
 * is typed is saved once the box is left, so a half typed word is never stored. A field that has to be
 * filled in is never cleared: leaving it empty puts back what it held.
 */
const value = defineModel<BoardFieldRaw | null>({ default: null })

const props = defineProps<{
    field: TypedBoardField
    members: MemberOption[]
    canEdit: boolean
}>()

const emit = defineEmits<{
    save: [fieldType: BoardFieldTypeName, value: BoardFieldRaw | null]
}>()

const { t } = useI18n()

const PICKED: readonly string[] = [FieldTypes.BOOLEAN, FieldTypes.CHOICE, FieldTypes.LANE_ASSIGNEE]

const held = ref<BoardFieldRaw | null>(value.value)

const options = computed(() => (props.field.fieldType === FieldTypes.CHOICE ? props.field.config.options : []))

const memberNames = computed(() => new Map(props.members.map(member => [Number(member.value), member.name])))

function write(text: string) {
    value.value = fieldValueOfText(props.field.fieldType, text)
    if (PICKED.includes(props.field.fieldType)) commit()
}

function commit() {
    if (value.value === held.value) return
    if (value.value === null && props.field.config.required) {
        value.value = held.value
        return
    }
    held.value = value.value
    emit('save', props.field.fieldType, value.value)
}
</script>

<template>
    <div data-testid="ticket-field" @focusin="held = value" @focusout="commit">
        <FieldLabel class="mb-1">{{ field.name }}</FieldLabel>
        <FieldAnswerInput
            v-if="canEdit"
            :field-type="field.fieldType"
            :members="members"
            :model-value="fieldValueText(value)"
            :options="options"
            :placeholder="field.fieldType === FieldTypes.LANE_ASSIGNEE ? t('boards.unassigned') : undefined"
            :required="field.config.required"
            @update:model-value="write"
        />
        <div v-else class="text-sm px-2 py-1">
            <QuestionValueDisplay :field-type="field.fieldType" :member-names="memberNames" :value="value" />
        </div>
    </div>
</template>
