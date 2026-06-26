/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup generic="K extends string">
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'

defineProps<{
  fields: { key: K; label: string }[]
  values: Record<K, string>
  keyPrefix: string
}>()

const emit = defineEmits<{
  (e: 'change', key: K, value: string): void
}>()

const {t} = useI18n()

function onInput(key: K, event: Event) {
  emit('change', key, (event.target as HTMLInputElement).value)
}
</script>

<template>
  <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
    <div v-for="field in fields" :key="keyPrefix + '-' + field.key" class="space-y-1">
      <FieldLabel class="text-xs">{{ t(`theme.${field.label}`) }}</FieldLabel>
      <div class="flex items-center gap-2">
        <input
            :value="values[field.key]"
            type="color"
            class="h-9 w-12 rounded-theme border border-(--border) cursor-pointer bg-transparent"
            @input="onInput(field.key, $event)"
        />
        <span class="text-xs text-(--text-muted) font-mono">{{ values[field.key] }}</span>
      </div>
    </div>
  </div>
</template>
