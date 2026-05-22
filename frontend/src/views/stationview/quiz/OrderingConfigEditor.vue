/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const { t } = useI18n()

const props = defineProps<{
  config: Record<string, unknown>
}>()

const emit = defineEmits<{
  'update:config': [value: Record<string, unknown>]
}>()

function updateConfig(patch: Record<string, unknown>) {
  emit('update:config', { ...props.config, ...patch })
}

// --- Ordering helpers ---
function addOrderingItem() {
  const items = [...((props.config.items as string[]) || [])]
  items.push('')
  updateConfig({ items })
}

function removeOrderingItem(idx: number) {
  const items = [...((props.config.items as string[]) || [])]
  items.splice(idx, 1)
  updateConfig({ items })
}

function updateOrderingItem(idx: number, value: string) {
  const items = [...((props.config.items as string[]) || [])]
  items[idx] = value
  updateConfig({ items })
}

function moveOrderingItem(idx: number, direction: -1 | 1) {
  const items = [...((props.config.items as string[]) || [])]
  const newIdx = idx + direction
  if (newIdx < 0 || newIdx >= items.length) return
  const temp = items[idx]
  items[idx] = items[newIdx]
  items[newIdx] = temp
  updateConfig({ items })
}
</script>

<template>
  <SubHeader>{{ t('quiz.questions.config.items') }}</SubHeader>
  <p class="text-xs text-(--text-muted)">{{ t('quiz.questions.config.orderingHint') }}</p>
  <div class="space-y-2">
    <div v-for="(item, idx) in (config.items as string[])" :key="idx" class="flex items-center gap-2">
      <span class="text-xs text-(--text-muted) shrink-0 w-5 text-right">{{ idx + 1 }}.</span>
      <TextInput :model-value="item" class="flex-1" @update:model-value="(v: string | undefined) => updateOrderingItem(idx, v ?? '')" />
      <IconButton :icon="['fas', 'chevron-up']" label="Up" :disabled="idx === 0" class="text-(--text-muted) hover:text-primary" @click="moveOrderingItem(idx, -1)" />
      <IconButton :icon="['fas', 'chevron-down']" label="Down" :disabled="idx === (config.items as string[]).length - 1" class="text-(--text-muted) hover:text-primary" @click="moveOrderingItem(idx, 1)" />
      <DeleteButton @click="removeOrderingItem(idx)" />
    </div>
    <SecondaryButton @click="addOrderingItem"><font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />{{ t('quiz.questions.config.addItem') }}</SecondaryButton>
  </div>
</template>
