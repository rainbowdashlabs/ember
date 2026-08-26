/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import {useI18n} from 'vue-i18n'
import DesktopFieldToggles from './DesktopFieldToggles.vue'
import {type ProfileField} from '@/api/profileFields'
import {isSection, widthOf} from '@/components/profilefields/fieldLayout'
import {widthLabel} from '../fieldTypes'
import {fieldGrid} from './fieldGrid'
import {useFieldsCapabilities, type WritabilityName} from '@/composables/useFieldsConfig'

defineProps<{
  field: ProfileField
  typeLabel: string
}>()

const {t} = useI18n()

const capabilities = useFieldsCapabilities()
const gridClass = computed(() => fieldGrid(capabilities.writability))

const emit = defineEmits<{
  edit: [field: ProfileField]
  delete: [field: ProfileField]
  toggleConfig: [field: ProfileField, key: string, value: boolean]
  toggleKeepOnArchive: [field: ProfileField, value: boolean]
  setWritability: [field: ProfileField, level: WritabilityName]
}>()
</script>

<template>
  <div
      :class="gridClass"
      class="gap-0 items-center border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 text-sm px-1 py-2">
    <div class="font-medium px-2 truncate">{{ field.name }}</div>
    <div class="text-(--text-muted) px-2 truncate text-xs">{{ typeLabel }}</div>
    <div class="text-(--text-muted) px-2 text-xs">{{ isSection(field) ? '' : widthLabel(t, widthOf(field)) }}</div>
    <DesktopFieldToggles
        :field="field"
        @toggle-config="(f, k, v) => emit('toggleConfig', f, k, v)"
        @toggle-keep-on-archive="(f, v) => emit('toggleKeepOnArchive', f, v)"
        @set-writability="(f, level) => emit('setWritability', f, level)"/>
    <div class="flex items-center justify-end gap-1">
      <EditButton @click="emit('edit', field)"/>
      <DeleteButton @click="emit('delete', field)"/>
    </div>
  </div>
</template>
