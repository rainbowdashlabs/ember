/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import DesktopFieldToggles from './DesktopFieldToggles.vue'
import {type ProfileField} from '@/api/types'

defineProps<{
  field: ProfileField
  typeLabel: string
}>()

const emit = defineEmits<{
  edit: [field: ProfileField]
  delete: [field: ProfileField]
  toggleConfig: [field: ProfileField, key: string, value: boolean]
  toggleKeepOnArchive: [field: ProfileField, value: boolean]
}>()
</script>

<template>
  <div
      class="grid grid-cols-[2rem_1fr_6rem_2.5rem_2.5rem_2.5rem_2.5rem_2.5rem_5rem] gap-0 items-center border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 text-sm px-1 py-2 cursor-grab active:cursor-grabbing">
    <div class="flex justify-center">
      <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) h-3.5 w-3.5"/>
    </div>
    <div class="font-medium px-2 truncate">{{ field.name }}</div>
    <div class="text-(--text-muted) px-2 truncate text-xs">{{ typeLabel }}</div>
    <DesktopFieldToggles
        :field="field"
        @toggle-config="(f, k, v) => emit('toggleConfig', f, k, v)"
        @toggle-keep-on-archive="(f, v) => emit('toggleKeepOnArchive', f, v)"/>
    <div class="flex items-center justify-end gap-1">
      <EditButton @click="emit('edit', field)"/>
      <DeleteButton @click="emit('delete', field)"/>
    </div>
  </div>
</template>
