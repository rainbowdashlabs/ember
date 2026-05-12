/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DragList from '@/components/input/DragList.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {AttendanceTemplateField, MemberGroup} from '@/api/types'

const props = defineProps<{
  fields: AttendanceTemplateField[]
  availableGroups: MemberGroup[]
}>()

const emit = defineEmits<{
  add: []
  edit: [field: AttendanceTemplateField]
  delete: [field: AttendanceTemplateField]
  reorder: [fromIndex: number, toIndex: number]
}>()

const {t} = useI18n()

const fieldTypeOptions: Record<string, string> = {
  string: 'Text',
  time: 'Uhrzeit',
  date: 'Datum',
  member: 'Mitglied',
  member_list: 'Mitgliederliste',
  member_of_group: 'Mitglied aus Gruppe',
  member_list_of_group: 'Mitgliederliste aus Gruppe',
  attendance: 'Anwesenheit',
  attendance_of_group: 'Anwesenheit einer Gruppe',
}

function fieldTypeLabel(value: string): string {
  return fieldTypeOptions[value] ?? value
}

function parseConfig(configStr: string | undefined): { groupId?: number; required?: boolean; autoAttend?: boolean } {
  if (!configStr) return {}
  try {
    return JSON.parse(configStr)
  } catch {
    return {}
  }
}

function groupName(groupId: number): string {
  return props.availableGroups.find(g => g.id === groupId)?.name ?? `#${groupId}`
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between">
      <SectionHeader>{{ t('attendanceConfig.fields') }}</SectionHeader>
      <PrimaryButton @click="emit('add')">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-2"/>
        {{ t('attendanceConfig.addField') }}
      </PrimaryButton>
    </div>

    <div v-if="fields.length === 0" class="text-center text-(--text-muted) py-8">
      {{ t('attendanceConfig.noFields') }}
    </div>

    <DragList :items="fields" :key-fn="(f) => f.id" @reorder="(from, to) => emit('reorder', from, to)">
      <template #default="{ item: field }">
        <div
            class="flex items-center justify-between cursor-grab active:cursor-grabbing rounded-lg border p-4 border-bg-light-accent bg-bg-light-accent/20 dark:border-bg-dark-accent dark:bg-bg-dark-accent/20 mb-2">
          <div class="flex items-center gap-3">
            <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) h-4 w-4"/>
            <div>
              <span class="font-medium">{{ field.name }}</span>
              <span v-if="parseConfig(field.config).required" class="ml-1 text-xs text-error">*</span>
              <span class="ml-2 text-sm text-(--text-muted)">({{ fieldTypeLabel(field.fieldType ?? '') }})</span>
              <span v-if="parseConfig(field.config).groupId" class="ml-1 text-xs text-(--text-muted)">
                — {{ groupName(parseConfig(field.config).groupId!) }}
              </span>
              <span v-if="parseConfig(field.config).autoAttend" class="ml-2 text-xs text-primary">
                <font-awesome-icon :icon="['fas', 'user-plus']" class="mr-0.5"/>auto
              </span>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <EditButton @click="emit('edit', field)"/>
            <DeleteButton @click="emit('delete', field)"/>
          </div>
        </div>
      </template>
    </DragList>
  </NeutralContainer>
</template>
