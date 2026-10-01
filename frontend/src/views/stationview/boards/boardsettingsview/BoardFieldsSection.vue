/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldTypePicker from '@/components/input/FieldTypePicker.vue'
import BoardFieldRow from './BoardFieldRow.vue'
import { OfferedFieldTypes } from '@/api/fieldTypes'
import { isBoardFieldType, type BoardFieldDraft, type BoardFieldTypeName } from '@/api/boards'
import type { LaneDraft } from './BoardLanesSection.vue'

defineProps<{
    fields: BoardFieldDraft[]
    lanes: LaneDraft[]
    newFieldName: string
    newFieldType: BoardFieldTypeName
}>()

const emit = defineEmits<{
    'update:newFieldName': [value: string]
    'update:newFieldType': [value: BoardFieldTypeName]
    add: []
    remove: [index: number]
    move: [index: number, dir: -1 | 1]
}>()

const { t } = useI18n()

function chooseNewType(value: unknown) {
    if (isBoardFieldType(value)) emit('update:newFieldType', value)
}
</script>

<template>
    <NeutralContainer>
        <SubHeader class="text-sm mb-3">{{ t('boards.fields') }}</SubHeader>
        <div class="space-y-2">
            <BoardFieldRow
                v-for="(field, index) in fields"
                :key="index"
                :field="field"
                :first="index === 0"
                :last="index === fields.length - 1"
                :lanes="lanes"
                @move="dir => emit('move', index, dir)"
                @remove="emit('remove', index)"
                @update="patch => Object.assign(field, patch)"
            />
        </div>
        <div class="flex gap-2 mt-3">
            <TextInput :model-value="newFieldName" :placeholder="t('boards.addField')" class="flex-1" @update:model-value="v => emit('update:newFieldName', String(v))" @keydown.enter="emit('add')" />
            <FieldTypePicker :model-value="newFieldType" :types="OfferedFieldTypes.BOARD" class="w-48"
                             @update:model-value="chooseNewType" />
            <SecondaryButton @click="emit('add')">
                <font-awesome-icon :icon="['fas', 'plus']" />
            </SecondaryButton>
        </div>
    </NeutralContainer>
</template>
