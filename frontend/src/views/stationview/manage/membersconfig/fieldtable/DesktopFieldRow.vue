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
import AudienceCount from './AudienceCount.vue'
import {type ProfileField} from '@/api/profileFields'
import {isSection, widthOf} from '@/components/profilefields/fieldLayout'
import {widthLabel} from '../fieldTypes'
import {fieldGrid} from './fieldGrid'
import {useFieldsCapabilities, type WritabilityName} from '@/composables/useFieldsConfig'

/**
 * @param audiences how many audiences are asked this question, which a row of none says out loud
 * @param selected  whether the panel beside this one is showing this question's audiences
 */
defineProps<{
  field: ProfileField
  typeLabel: string
  audiences: number
  selected: boolean
  checked: boolean
}>()

const {t} = useI18n()

const emit = defineEmits<{
  select: [field: ProfileField]
  toggleChecked: [field: ProfileField]
  edit: [field: ProfileField]
  delete: [field: ProfileField]
  toggleConfig: [field: ProfileField, key: string, value: boolean]
  toggleKeepOnArchive: [field: ProfileField, value: boolean]
  toggleRequired: [field: ProfileField, value: boolean]
  toggleReadonly: [field: ProfileField, value: boolean]
  setWritability: [field: ProfileField, level: WritabilityName]
}>()

const capabilities = useFieldsCapabilities()
const gridClass = computed(() => fieldGrid(capabilities.writability))
</script>

<template>
  <div
      :class="[gridClass, selected ? 'bg-primary/10' : 'hover:bg-bg-light-accent/40 dark:hover:bg-bg-dark-accent/40']"
      :data-testid="`field-row-${field.name}`"
      class="gap-0 items-center border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 text-sm px-1 py-2 cursor-pointer"
      @click="emit('select', field)">
    <div class="font-medium px-2 truncate flex items-center gap-2">
      <input
          type="checkbox"
          class="cursor-pointer"
          :checked="checked"
          :aria-label="t('membersConfig.batch.toggleRow')"
          :data-testid="`field-check-${field.name}`"
          @click.stop="emit('toggleChecked', field)"/>
      {{ field.name }}
      <AudienceCount :count="audiences"/>
    </div>
    <div class="text-(--text-muted) px-2 truncate text-xs">{{ typeLabel }}</div>
    <div class="text-(--text-muted) px-2 text-xs">{{ isSection(field) ? '' : widthLabel(t, widthOf(field)) }}</div>
    <DesktopFieldToggles
        :field="field"
        @click.stop
        @toggle-config="(f, k, v) => emit('toggleConfig', f, k, v)"
        @toggle-keep-on-archive="(f, v) => emit('toggleKeepOnArchive', f, v)"
        @toggle-required="(f, v) => emit('toggleRequired', f, v)"
        @toggle-readonly="(f, v) => emit('toggleReadonly', f, v)"
        @set-writability="(f, level) => emit('setWritability', f, level)"/>
    <div class="flex items-center justify-end gap-1" @click.stop>
      <EditButton @click="emit('edit', field)"/>
      <DeleteButton @click="emit('delete', field)"/>
    </div>
  </div>
</template>
