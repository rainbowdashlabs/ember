/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldTypePicker from '@/components/input/FieldTypePicker.vue'
import QuestionOptionsEditor from '@/components/input/QuestionOptionsEditor.vue'
import { FieldTypes, OfferedFieldTypes } from '@/api/fieldTypes'
import { isBoardFieldType, type BoardFieldDraft } from '@/api/boards'
import type { LaneDraft } from './BoardLanesSection.vue'

/**
 * One field of a board as its settings edit it: the name, the type picked the way every field's type
 * is picked, whether it has to be filled in, and what only its type needs, the answers of a choice one
 * to a line and the lane a ticket's assignee is taken in.
 */
defineProps<{
    field: BoardFieldDraft
    lanes: LaneDraft[]
    first: boolean
    last: boolean
}>()

const emit = defineEmits<{
    update: [patch: Partial<BoardFieldDraft>]
    move: [dir: -1 | 1]
    remove: []
}>()

const { t } = useI18n()

function retype(value: unknown) {
    if (isBoardFieldType(value)) emit('update', { fieldType: value })
}
</script>

<template>
    <div class="space-y-2 border border-[var(--border)] rounded-theme p-2" data-testid="board-field">
        <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-[var(--text-muted)] cursor-grab" />
            <TextInput :model-value="field.name" :placeholder="t('boards.fieldName')" class="flex-1"
                       @update:model-value="v => emit('update', { name: String(v ?? '') })" />
            <FieldTypePicker :model-value="field.fieldType" :types="OfferedFieldTypes.BOARD" class="w-48"
                             @update:model-value="retype" />
            <IconButton :icon="['fas', 'chevron-up']" label="Move up" :disabled="first" @click="emit('move', -1)" />
            <IconButton :icon="['fas', 'chevron-down']" label="Move down" :disabled="last" @click="emit('move', 1)" />
            <IconButton :icon="['fas', 'xmark']" label="Remove" @click="emit('remove')" />
        </div>
        <label class="flex items-center gap-2 text-sm pl-6">
            <ToggleInput :model-value="field.required" data-testid="board-field-required"
                         @update:model-value="required => emit('update', { required })" />
            {{ t('questionSettings.required') }}
        </label>
        <QuestionOptionsEditor
            v-if="field.fieldType === FieldTypes.CHOICE"
            :label="t('questionSettings.options')"
            :model-value="field.options"
            class="pl-6"
            @update:model-value="options => emit('update', { options })"
        />
        <div v-if="field.fieldType === FieldTypes.LANE_ASSIGNEE" class="pl-6">
            <FieldLabel class="text-xs mb-1">{{ t('boards.fieldLane') }}</FieldLabel>
            <SelectInput :model-value="String(field.laneId ?? '')"
                         @update:model-value="v => emit('update', { laneId: v ? Number(v) : null })">
                <option value="">-</option>
                <option v-for="lane in lanes" :key="lane.id" :value="String(lane.id)">{{ lane.name }}</option>
            </SelectInput>
        </div>
    </div>
</template>
