/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import FieldHint from '@/components/typography/FieldHint.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'

const items = defineModel<string[]>('items', {required: true})

defineProps<{
  label: string
  addLabel: string
}>()

function addItem() {
  items.value = [...items.value, '']
}

function removeItem(index: number) {
  const next = [...items.value]
  next.splice(index, 1)
  items.value = next
}

function updateItem(index: number, value: string) {
  const next = [...items.value]
  next[index] = value
  items.value = next
}

function moveItem(index: number, direction: -1 | 1) {
  const newIdx = index + direction
  const next = [...items.value]
  const current = next[index]
  const target = next[newIdx]
  if (current === undefined || target === undefined) return
  next[index] = target
  next[newIdx] = current
  items.value = next
}
</script>

<template>
  <div class="space-y-1">
    <FieldHint>{{ label }}</FieldHint>
    <div v-for="(item, idx) in items" :key="idx" class="flex gap-2 items-center">
      <TextInput :model-value="item" class="flex-1"
                 @update:model-value="(v: string | undefined) => updateItem(idx, v ?? '')" />
      <MutedIconButton :icon="['fas', 'chevron-up']" label="Up"
                       @click="moveItem(idx, -1)" />
      <MutedIconButton :icon="['fas', 'chevron-down']" label="Down"
                       @click="moveItem(idx, 1)" />
      <DeleteButton @click="removeItem(idx)" />
    </div>
    <SecondaryButton @click="addItem">{{ addLabel }}</SecondaryButton>
  </div>
</template>
