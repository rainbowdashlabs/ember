/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import type {EyeDirection} from '@/components/display/LayeredEmberLogo.vue'

const props = defineProps<{
  positions: EyeDirection[]
}>()

const emit = defineEmits<{
  toggle: [dir: EyeDirection]
}>()

const gazeDirectionOptions: { key: EyeDirection; label: string }[] = [
  { key: 'left', label: 'Links' },
  { key: 'mid', label: 'Mitte' },
  { key: 'right', label: 'Rechts' },
]
</script>

<template>
  <div>
    <p class="text-xs font-medium text-(--text-muted) mb-1">Blickrichtung</p>
    <div class="flex flex-wrap gap-2">
      <SelectionToggleButton
        v-for="opt in gazeDirectionOptions"
        :key="opt.key"
        size="sm"
        :selected="positions.includes(opt.key)"
        @toggle="emit('toggle', opt.key)"
      >
        {{ opt.label }}
      </SelectionToggleButton>
    </div>
    <p v-if="props.positions.length === 1" class="text-xs text-(--text-muted) mt-1">
      Noch eine Richtung hinzufuegen um die Animation zu starten.
    </p>
  </div>
</template>
