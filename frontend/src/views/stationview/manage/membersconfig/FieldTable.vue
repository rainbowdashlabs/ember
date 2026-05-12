/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import DragList from '@/components/input/DragList.vue'
import type {ProfileField} from '@/api/types'

const {t} = useI18n()

defineProps<{
  fields: ProfileField[]
}>()

const emit = defineEmits<{
  edit: [field: ProfileField]
  delete: [field: ProfileField]
  reorder: [fromIndex: number, toIndex: number]
  toggleConfig: [field: ProfileField, key: string, value: boolean]
}>()

const fieldTypeOptions = [
  {value: 'text', label: 'Text'},
  {value: 'number', label: 'Zahl'},
  {value: 'date', label: 'Datum'},
  {value: 'boolean', label: 'Ja/Nein'},
  {value: 'enum', label: 'Auswahl'},
  {value: 'age', label: 'Alter (berechnet)'},
]

function fieldTypeLabel(value: string): string {
  return fieldTypeOptions.find(o => o.value === value)?.label ?? value
}

function parseConfig(configStr: string | undefined): Record<string, unknown> {
  if (!configStr) return {}
  try {
    return JSON.parse(configStr)
  } catch {
    return {}
  }
}
</script>

<template>
  <div>
    <!-- Header row -->
    <div
        class="grid grid-cols-[2rem_1fr_6rem_2.5rem_2.5rem_2.5rem_2.5rem_5rem] gap-0 items-center border-b border-bg-light-accent dark:border-bg-dark-accent text-sm px-1 py-2">
      <div></div>
      <div class="font-medium px-2">{{ t('membersConfig.colName') }}</div>
      <div class="font-medium px-2">{{ t('membersConfig.colType') }}</div>
      <div :title="t('membersConfig.fieldRequired')" class="text-center">
        <font-awesome-icon :icon="['fas', 'asterisk']" class="h-3 w-3 text-error"/>
      </div>
      <div :title="t('membersConfig.fieldReadonly')" class="text-center">
        <font-awesome-icon :icon="['fas', 'lock']" class="h-3 w-3"/>
      </div>
      <div :title="t('membersConfig.fieldNotifyOnChange')" class="text-center">
        <font-awesome-icon :icon="['fas', 'bell']" class="h-3 w-3"/>
      </div>
      <div :title="t('membersConfig.fieldOverview')" class="text-center">
        <font-awesome-icon :icon="['fas', 'list']" class="h-3 w-3"/>
      </div>
      <div></div>
    </div>

    <!-- Rows -->
    <DragList :items="fields" :key-fn="(f) => f.id" @reorder="(from, to) => emit('reorder', from, to)">
      <template #default="{ item: field }">
        <div
            class="grid grid-cols-[2rem_1fr_6rem_2.5rem_2.5rem_2.5rem_2.5rem_5rem] gap-0 items-center border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 text-sm px-1 py-2 cursor-grab active:cursor-grabbing">
          <div class="flex justify-center">
            <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) h-3.5 w-3.5"/>
          </div>
          <div class="font-medium px-2 truncate">{{ field.name }}</div>
          <div class="text-(--text-muted) px-2 truncate text-xs">{{ fieldTypeLabel(field.fieldType ?? '') }}</div>
          <div class="flex justify-center">
            <input :checked="!!parseConfig(field.config).required" class="h-4 w-4 rounded border-2 border-bg-light-accent dark:border-bg-dark-accent accent-primary cursor-pointer"
                   type="checkbox"
                   @change="emit('toggleConfig', field, 'required', ($event.target as HTMLInputElement).checked)"
                   @click.stop/>
          </div>
          <div class="flex justify-center">
            <input :checked="!!parseConfig(field.config).readonly" class="h-4 w-4 rounded border-2 border-bg-light-accent dark:border-bg-dark-accent accent-primary cursor-pointer"
                   type="checkbox"
                   @change="emit('toggleConfig', field, 'readonly', ($event.target as HTMLInputElement).checked)"
                   @click.stop/>
          </div>
          <div class="flex justify-center">
            <input :checked="!!parseConfig(field.config).notifyOnChange" class="h-4 w-4 rounded border-2 border-bg-light-accent dark:border-bg-dark-accent accent-primary cursor-pointer"
                   type="checkbox"
                   @change="emit('toggleConfig', field, 'notifyOnChange', ($event.target as HTMLInputElement).checked)"
                   @click.stop/>
          </div>
          <div class="flex justify-center">
            <input :checked="!!parseConfig(field.config).overview" class="h-4 w-4 rounded border-2 border-bg-light-accent dark:border-bg-dark-accent accent-primary cursor-pointer"
                   type="checkbox"
                   @change="emit('toggleConfig', field, 'overview', ($event.target as HTMLInputElement).checked)"
                   @click.stop/>
          </div>
          <div class="flex items-center justify-end gap-1">
            <EditButton @click="emit('edit', field)"/>
            <DeleteButton @click="emit('delete', field)"/>
          </div>
        </div>
      </template>
    </DragList>
  </div>
</template>
