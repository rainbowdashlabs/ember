/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SaveButton from '@/components/button/SaveButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import GroupsEditor from './GroupsEditor.vue'
import FieldsList from './FieldsList.vue'
import type {AttendanceTemplateField, MemberGroup, TemplateGroupEntry} from '@/api/types'

const props = defineProps<{
  isEdit: boolean
  name: string
  fields: AttendanceTemplateField[]
  templateGroups: TemplateGroupEntry[]
  availableGroups: MemberGroup[]
  saveTemplate: () => Promise<void>
}>()

const emit = defineEmits<{
  (e: 'update:name', value: string): void
  (e: 'add-group', groupId: number): void
  (e: 'remove-group', groupId: number): void
  (e: 'move-group-up', index: number): void
  (e: 'move-group-down', index: number): void
  (e: 'add-field'): void
  (e: 'edit-field', field: AttendanceTemplateField): void
  (e: 'delete-field', field: AttendanceTemplateField): void
  (e: 'reorder-fields', fromIndex: number, toIndex: number): void
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-6">
    <NeutralContainer class="space-y-4">
      <SectionHeader>
        {{ props.isEdit ? t('attendanceConfig.editTitle') : t('attendanceConfig.createTitle') }}
      </SectionHeader>
      <div class="space-y-1">
        <FieldLabel>{{ t('attendanceConfig.name') }}</FieldLabel>
        <TextInput
            :model-value="props.name"
            :placeholder="t('attendanceConfig.namePlaceholder')"
            @update:model-value="(v: string) => emit('update:name', v)"
        />
      </div>
      <SaveButton :disabled="!props.name" :action="props.saveTemplate">
        {{ props.isEdit ? t('attendanceConfig.save') : t('attendanceConfig.createSubmit') }}
      </SaveButton>
    </NeutralContainer>

    <GroupsEditor
        v-if="props.isEdit"
        :available-groups="props.availableGroups"
        :groups="props.templateGroups"
        @add="(id: number) => emit('add-group', id)"
        @remove="(id: number) => emit('remove-group', id)"
        @move-up="(i: number) => emit('move-group-up', i)"
        @move-down="(i: number) => emit('move-group-down', i)"
    />

    <FieldsList
        v-if="props.isEdit"
        :available-groups="props.availableGroups"
        :fields="props.fields"
        @add="emit('add-field')"
        @delete="(f: AttendanceTemplateField) => emit('delete-field', f)"
        @edit="(f: AttendanceTemplateField) => emit('edit-field', f)"
        @reorder="(from: number, to: number) => emit('reorder-fields', from, to)"
    />
  </div>
</template>
