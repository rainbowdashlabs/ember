/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import DragList from '@/components/input/DragList.vue'
import DesktopHeaderRow from './fieldtable/DesktopHeaderRow.vue'
import DesktopFieldRow from './fieldtable/DesktopFieldRow.vue'
import MobileFieldCard from './fieldtable/MobileFieldCard.vue'
import {type ProfileField} from '@/api/profileFields'
import {useBreakpoint} from '@/composables/useBreakpoint'

const {isMobile} = useBreakpoint()

defineProps<{
  fields: ProfileField[]
}>()

const emit = defineEmits<{
  edit: [field: ProfileField]
  delete: [field: ProfileField]
  reorder: [fromIndex: number, toIndex: number]
  toggleConfig: [field: ProfileField, key: string, value: boolean]
  toggleKeepOnArchive: [field: ProfileField, value: boolean]
}>()

const fieldTypeOptions = [
  {value: 'TEXT', label: 'Text'},
  {value: 'NUMBER', label: 'Zahl'},
  {value: 'DATE', label: 'Datum'},
  {value: 'BIRTH_DATE', label: 'Geburtsdatum'},
  {value: 'BOOLEAN', label: 'Ja/Nein'},
  {value: 'ENUM', label: 'Auswahl'},
  {value: 'AGE', label: 'Alter (berechnet)'},
]

function fieldTypeLabel(value: string): string {
  return fieldTypeOptions.find(o => o.value === value)?.label ?? value
}
</script>

<template>
  <div v-if="isMobile">
    <DragList :items="fields" :key-fn="(f) => f.id" @reorder="(from, to) => emit('reorder', from, to)">
      <template #default="{ item: field }">
        <MobileFieldCard
            :field="field"
            :type-label="fieldTypeLabel(field.fieldType ?? '')"
            @delete="emit('delete', field)"
            @edit="emit('edit', field)"
            @toggle-config="(f, k, v) => emit('toggleConfig', f, k, v)"
            @toggle-keep-on-archive="(f, v) => emit('toggleKeepOnArchive', f, v)"/>
      </template>
    </DragList>
  </div>

  <div v-else>
    <DesktopHeaderRow/>
    <DragList :items="fields" :key-fn="(f) => f.id" @reorder="(from, to) => emit('reorder', from, to)">
      <template #default="{ item: field }">
        <DesktopFieldRow
            :field="field"
            :type-label="fieldTypeLabel(field.fieldType ?? '')"
            @delete="emit('delete', field)"
            @edit="emit('edit', field)"
            @toggle-config="(f, k, v) => emit('toggleConfig', f, k, v)"
            @toggle-keep-on-archive="(f, v) => emit('toggleKeepOnArchive', f, v)"/>
      </template>
    </DragList>
  </div>
</template>
