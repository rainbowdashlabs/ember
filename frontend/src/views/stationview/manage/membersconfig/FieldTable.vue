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
import {useI18n} from 'vue-i18n'
import {fieldTypeLabel} from './fieldTypes'
import {type WritabilityName} from '@/composables/useFieldsConfig'

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
  setWritability: [field: ProfileField, level: WritabilityName]
}>()

const {t} = useI18n()

function typeLabel(value: string): string {
  return fieldTypeLabel(t, value)
}
</script>

<template>
  <div v-if="isMobile">
    <DragList :items="fields" :key-fn="(f) => f.id" @reorder="(from, to) => emit('reorder', from, to)">
      <template #default="{ item: field }">
        <MobileFieldCard
            :field="field"
            :type-label="typeLabel(field.fieldType ?? '')"
            @delete="emit('delete', field)"
            @edit="emit('edit', field)"
            @toggle-config="(f, k, v) => emit('toggleConfig', f, k, v)"
            @toggle-keep-on-archive="(f, v) => emit('toggleKeepOnArchive', f, v)"
            @set-writability="(f, level) => emit('setWritability', f, level)"/>
      </template>
    </DragList>
  </div>

  <div v-else>
    <DesktopHeaderRow/>
    <DragList :items="fields" :key-fn="(f) => f.id" @reorder="(from, to) => emit('reorder', from, to)">
      <template #default="{ item: field }">
        <DesktopFieldRow
            :field="field"
            :type-label="typeLabel(field.fieldType ?? '')"
            @delete="emit('delete', field)"
            @edit="emit('edit', field)"
            @toggle-config="(f, k, v) => emit('toggleConfig', f, k, v)"
            @toggle-keep-on-archive="(f, v) => emit('toggleKeepOnArchive', f, v)"
            @set-writability="(f, level) => emit('setWritability', f, level)"/>
      </template>
    </DragList>
  </div>
</template>
