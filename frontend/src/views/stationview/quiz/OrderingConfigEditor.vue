/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import DecimalInput from '@/components/input/number/DecimalInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldHint from '@/components/typography/FieldHint.vue'

const { t } = useI18n()

const config = defineModel<Record<string, unknown>>('config', {required: true})

function updateConfig(patch: Record<string, unknown>) {
  config.value = { ...config.value, ...patch }
}

function addOrderingItem() {
  const items = [...((config.value.items as string[]) || [])]
  items.push('')
  updateConfig({ items })
}

function removeOrderingItem(idx: number) {
  const items = [...((config.value.items as string[]) || [])]
  items.splice(idx, 1)
  updateConfig({ items })
}

function updateOrderingItem(idx: number, value: string) {
  const items = [...((config.value.items as string[]) || [])]
  items[idx] = value
  updateConfig({ items })
}

function moveOrderingItem(idx: number, direction: -1 | 1) {
  const items = [...((config.value.items as string[]) || [])]
  const newIdx = idx + direction
  const current = items[idx]
  const target = items[newIdx]
  if (current === undefined || target === undefined) return
  items[idx] = target
  items[newIdx] = current
  updateConfig({ items })
}
</script>

<template>
  <div class="flex items-center gap-2">
    <FieldHint>{{ t('quiz.questions.config.pointsPerCorrect') }}</FieldHint>
    <DecimalInput :model-value="(config.pointsPerCorrect as number) || 1" step="0.5" class="w-20" @update:model-value="(v: number | undefined) => updateConfig({ pointsPerCorrect: v ?? 1 })"/>
  </div>
  <SubHeader>{{ t('quiz.questions.config.items') }}</SubHeader>
  <p class="text-xs text-(--text-muted)">{{ t('quiz.questions.config.orderingHint') }}</p>
  <div class="space-y-2">
    <div v-for="(item, idx) in (config.items as string[])" :key="idx" class="flex items-center gap-2">
      <span class="text-xs text-(--text-muted) shrink-0 w-5 text-right">{{ idx + 1 }}.</span>
      <TextInput :model-value="item" class="flex-1" @update:model-value="(v: string | undefined) => updateOrderingItem(idx, v ?? '')" />
      <MutedIconButton :icon="['fas', 'chevron-up']" label="Up" :disabled="idx === 0" @click="moveOrderingItem(idx, -1)" />
      <MutedIconButton :icon="['fas', 'chevron-down']" label="Down" :disabled="idx === (config.items as string[]).length - 1" @click="moveOrderingItem(idx, 1)" />
      <DeleteButton @click="removeOrderingItem(idx)" />
    </div>
    <SecondaryButton @click="addOrderingItem"><font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />{{ t('quiz.questions.config.addItem') }}</SecondaryButton>
  </div>
</template>
