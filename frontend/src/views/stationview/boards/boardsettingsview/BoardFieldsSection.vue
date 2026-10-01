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
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import { isBoardFieldType, type BoardFieldDraft, type BoardFieldTypeName } from '@/api/boards'
import type { LaneDraft } from './BoardLanesSection.vue'

export interface FieldTypeOption {
    value: BoardFieldTypeName
    label: string
}

defineProps<{
    fields: BoardFieldDraft[]
    lanes: LaneDraft[]
    newFieldName: string
    newFieldType: BoardFieldTypeName
    fieldTypeOptions: FieldTypeOption[]
}>()

const emit = defineEmits<{
    'update:newFieldName': [value: string]
    'update:newFieldType': [value: BoardFieldTypeName]
    add: []
    remove: [index: number]
    move: [index: number, dir: -1 | 1]
}>()

const { t } = useI18n()
</script>

<template>
    <NeutralContainer>
        <SubHeader class="text-sm mb-3">{{ t('boards.fields') }}</SubHeader>
        <div class="space-y-2">
            <div v-for="(field, index) in fields" :key="index" class="space-y-1 border border-[var(--border)] rounded-theme p-2">
                <div class="flex items-center gap-2">
                    <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-[var(--text-muted)] cursor-grab" />
                    <TextInput v-model="field.name" :placeholder="t('boards.fieldName')" class="flex-1" />
                    <SelectInput :model-value="field.fieldType" @update:model-value="v => { if (isBoardFieldType(v)) field.fieldType = v }">
                        <option v-for="ft in fieldTypeOptions" :key="ft.value" :value="ft.value">{{ t(ft.label) }}</option>
                    </SelectInput>
                    <IconButton :icon="['fas', 'chevron-up']" label="Move up" :disabled="index === 0" @click="emit('move', index, -1)" />
                    <IconButton :icon="['fas', 'chevron-down']" label="Move down" :disabled="index === fields.length - 1" @click="emit('move', index, 1)" />
                    <IconButton :icon="['fas', 'xmark']" label="Remove" @click="emit('remove', index)" />
                </div>
                <div v-if="field.fieldType === 'ENUM'" class="pl-6">
                    <TextInput
                        :model-value="field.options.join(', ')"
                        :placeholder="t('boards.fieldOptions')"
                        class="text-sm"
                        @update:model-value="v => field.options = String(v).split(',').map(s => s.trim()).filter(Boolean)"
                    />
                </div>
                <div v-if="field.fieldType === 'LANE_ASSIGNEE'" class="pl-6">
                    <FieldLabel class="text-xs mb-1">{{ t('boards.fieldLane') }}</FieldLabel>
                    <SelectInput :model-value="String(field.laneId ?? '')" @update:model-value="v => field.laneId = v ? Number(v) : null">
                        <option value="">-</option>
                        <option v-for="lane in lanes" :key="lane.id" :value="String(lane.id)">{{ lane.name }}</option>
                    </SelectInput>
                </div>
            </div>
        </div>
        <div class="flex gap-2 mt-3">
            <TextInput :model-value="newFieldName" :placeholder="t('boards.addField')" class="flex-1" @update:model-value="v => emit('update:newFieldName', String(v))" @keydown.enter="emit('add')" />
            <SelectInput :model-value="newFieldType" @update:model-value="v => { if (isBoardFieldType(v)) emit('update:newFieldType', v) }">
                <option v-for="ft in fieldTypeOptions" :key="ft.value" :value="ft.value">{{ t(ft.label) }}</option>
            </SelectInput>
            <SecondaryButton @click="emit('add')">
                <font-awesome-icon :icon="['fas', 'plus']" />
            </SecondaryButton>
        </div>
    </NeutralContainer>
</template>
