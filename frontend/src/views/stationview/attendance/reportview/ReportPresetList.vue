/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import {StationUserTypeLabels, type MemberGroup} from '@/api/types'
import type {AttendanceReportPreset} from '@/api/generated/schema'

const props = defineProps<{
  presets: AttendanceReportPreset[]
  groups: MemberGroup[]
}>()

const emit = defineEmits<{
  apply: [preset: AttendanceReportPreset]
  remove: [id: number]
}>()

const {t} = useI18n()

/** The preset's name, followed by every user type and every group it selects that still exists. */
function presetLabel(preset: AttendanceReportPreset): string {
  const types = preset.userTypes.map(type => StationUserTypeLabels[type] ?? type)
  const groups = preset.groupIds
      .map(id => props.groups.find(g => g.id === id)?.name)
      .filter((name): name is string => !!name)
  const parts = [...types, ...groups]
  return parts.length > 0 ? `${preset.name} (${parts.join(', ')})` : preset.name
}
</script>

<template>
  <div v-if="presets.length > 0" class="flex flex-wrap gap-2">
    <SelectionToggleButton
        v-for="preset in presets"
        :key="preset.id"
        :selected="false"
        class="flex items-center gap-2"
        @toggle="emit('apply', preset)"
    >
      {{ presetLabel(preset) }}
      <span
          role="button"
          tabindex="0"
          :aria-label="t('common.remove')"
          class="text-(--text-muted) hover:text-error cursor-pointer"
          @click.stop="emit('remove', preset.id)"
          @keydown.enter.space.stop.prevent="emit('remove', preset.id)"
      >
        <font-awesome-icon :icon="['fas', 'xmark']" class="h-3 w-3"/>
      </span>
    </SelectionToggleButton>
  </div>
</template>
