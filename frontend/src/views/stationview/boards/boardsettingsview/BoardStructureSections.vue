/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BoardGeneralSection from './BoardGeneralSection.vue'
import BoardLanesSection from './BoardLanesSection.vue'
import BoardFieldsSection from './BoardFieldsSection.vue'
import type { LaneDraft } from './BoardLanesSection.vue'
import type { FieldTypeOption } from './BoardFieldsSection.vue'
import type { BoardFieldDraft, BoardFieldTypeName } from '@/api/boards'

defineProps<{
    lanes: LaneDraft[]
    fields: BoardFieldDraft[]
    fieldTypeOptions: FieldTypeOption[]
}>()

const name = defineModel<string>('name', { required: true })
const description = defineModel<string>('description', { required: true })
const hideDoneAfterDays = defineModel<number>('hideDoneAfterDays', { required: true })
const hasBacklog = defineModel<boolean>('hasBacklog', { required: true })
const newLaneName = defineModel<string>('newLaneName', { required: true })
const newFieldName = defineModel<string>('newFieldName', { required: true })
const newFieldType = defineModel<BoardFieldTypeName>('newFieldType', { required: true })

const emit = defineEmits<{
    addLane: []
    removeLane: [index: number]
    moveLane: [index: number, dir: -1 | 1]
    addField: []
    removeField: [index: number]
    moveField: [index: number, dir: -1 | 1]
}>()
</script>

<template>
    <div class="space-y-6">
        <BoardGeneralSection
            v-model:name="name"
            v-model:description="description"
            v-model:hide-done-after-days="hideDoneAfterDays"
            v-model:has-backlog="hasBacklog"
        />
        <BoardLanesSection
            :lanes="lanes"
            :new-lane-name="newLaneName"
            @update:new-lane-name="v => newLaneName = v"
            @add="emit('addLane')"
            @remove="i => emit('removeLane', i)"
            @move="(i, d) => emit('moveLane', i, d)"
        />
        <BoardFieldsSection
            :fields="fields"
            :lanes="lanes"
            :new-field-name="newFieldName"
            :new-field-type="newFieldType"
            :field-type-options="fieldTypeOptions"
            @update:new-field-name="v => newFieldName = v"
            @update:new-field-type="v => newFieldType = v"
            @add="emit('addField')"
            @remove="i => emit('removeField', i)"
            @move="(i, d) => emit('moveField', i, d)"
        />
    </div>
</template>
