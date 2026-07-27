/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup generic="K extends string | number">
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {ExportFieldOption} from '@/composables/useExport'

const {t} = useI18n()

defineProps<{
  options: ExportFieldOption<K>[]
  selected: Set<K>
  label?: string
  bulk?: boolean
  boxed?: boolean
  layout?: 'grid' | 'inline'
}>()

const emit = defineEmits<{
  toggle: [key: K]
  select: [keys: K[]]
}>()
</script>

<template>
  <component :is="boxed ? NeutralContainer : 'div'" class="space-y-2">
    <div v-if="label || bulk" class="flex items-center justify-between gap-2">
      <FieldLabel v-if="label">{{ label }}</FieldLabel>
      <div v-if="bulk" class="flex items-center gap-2">
        <SecondaryButton @click="emit('select', options.map(o => o.key))">{{ t('common.selectAll') }}</SecondaryButton>
        <SecondaryButton @click="emit('select', [])">{{ t('common.selectNone') }}</SecondaryButton>
      </div>
    </div>

    <MutedText v-if="options.length === 0" tag="p">{{ t('common.noOptions') }}</MutedText>

    <div v-else :class="layout === 'inline'
      ? 'flex flex-wrap gap-2'
      : 'grid gap-1 sm:grid-cols-2 max-h-64 overflow-y-auto'">
      <FieldLabel
          v-for="option in options"
          :key="option.key"
          inline
          class="cursor-pointer rounded px-2 py-1 hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30">
        <CheckboxInput :model-value="selected.has(option.key)" @update:model-value="emit('toggle', option.key)"/>
        {{ option.label }}
      </FieldLabel>
    </div>
  </component>
</template>
