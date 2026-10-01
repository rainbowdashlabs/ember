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
import UserTypesEditor from './UserTypesEditor.vue'
import FieldsList from './FieldsList.vue'
import type {AttendanceTemplateField, TemplateGroupEntry} from '@/api/generated/schema'
import type {MemberGroup, StationUserTypeName} from '@/api/types'

const props = defineProps<{
  isEdit: boolean
  name: string
  fields: AttendanceTemplateField[]
  templateGroups: TemplateGroupEntry[]
  templateUserTypes: StationUserTypeName[]
  availableGroups: MemberGroup[]
  saveTemplate: () => Promise<void>
}>()

const emit = defineEmits<{
  'update:name': [value: string]
  'update-user-types': [userTypes: StationUserTypeName[]]
  'add-group': [groupId: number]
  'remove-group': [groupId: number]
  'reorder-groups': [fromIndex: number, toIndex: number]
  'add-field': []
  'edit-field': [field: AttendanceTemplateField]
  'delete-field': [field: AttendanceTemplateField]
  'reorder-fields': [fromIndex: number, toIndex: number]
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
            @update:model-value="(v: string | undefined) => emit('update:name', v ?? '')"
        />
      </div>
      <SaveButton :disabled="!props.name" :action="props.saveTemplate">
        {{ props.isEdit ? t('attendanceConfig.save') : t('attendanceConfig.createSubmit') }}
      </SaveButton>
    </NeutralContainer>

    <UserTypesEditor
        v-if="props.isEdit"
        :model-value="props.templateUserTypes"
        @update:model-value="(types: StationUserTypeName[]) => emit('update-user-types', types)"
    />

    <GroupsEditor
        v-if="props.isEdit"
        :available-groups="props.availableGroups"
        :groups="props.templateGroups"
        @add="(id: number) => emit('add-group', id)"
        @remove="(id: number) => emit('remove-group', id)"
        @reorder="(from: number, to: number) => emit('reorder-groups', from, to)"
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
