/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import type {MemberGroup} from '@/api/types'
import type {ReportPreset} from '@/api/attendance'

const props = defineProps<{
  presets: ReportPreset[]
  groups: MemberGroup[]
}>()

const emit = defineEmits<{
  apply: [preset: ReportPreset]
  remove: [id: number]
}>()

function presetLabel(preset: ReportPreset): string {
  const parts: string[] = []
  if (preset.roleName) parts.push(preset.roleName)
  if (preset.groupId) {
    const g = props.groups.find(g => g.id === preset.groupId)
    parts.push(g?.name ?? 'Gruppe')
  }
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
      <span class="text-(--text-muted) hover:text-error cursor-pointer" @click.stop="emit('remove', preset.id)">
        <font-awesome-icon :icon="['fas', 'xmark']" class="h-3 w-3"/>
      </span>
    </SelectionToggleButton>
  </div>
</template>
