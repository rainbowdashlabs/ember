/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onBeforeUnmount, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * The size ranges a station actually stocks, picked by dragging across them.
 *
 * <p>Typing twenty sizes one at a time is the slowest part of setting up an inventory, and every
 * station types the same four ranges: letter sizes, body heights, the ordinary chest sizes and the
 * short ones. Here they are already written down, and picking a run of them is one drag.
 *
 * <p>They go in in the order they are shown, never the order they were touched, because the order is
 * what the rest of the product reads as "one size larger". Somebody dragging right to left would
 * otherwise record their sizes backwards and every exchange would then offer a smaller one.
 */
const emit = defineEmits<{
  add: [labels: string[]]
  close: []
}>()

const {t} = useI18n()

/** A row of the field, in the order the sizes belong in. */
interface Row {
  key: string
  labels: string[]
}

function range(from: number, to: number, step: number): string[] {
  const out: string[] = []
  for (let value = from; value <= to; value += step) out.push(String(value))
  return out
}

const ROWS: Row[] = [
    {key: 'letters', labels: ['4XS', '3XS', '2XS', 'XS', 'S', 'M', 'L', 'XL', '2XL', '3XL', '4XL']},
    {key: 'heights', labels: range(116, 188, 6)},
    {key: 'chest', labels: range(44, 74, 2)},
    {key: 'short', labels: range(90, 122, 4)},
]

const picked = ref<Set<string>>(new Set())
const dragging = ref(false)

/**
 * Everything picked, in the order the field shows it rather than the order it was touched.
 *
 * <p>Named once even where two rows carry the same number: 122 is both a body height and a short size,
 * and a size list holding it twice would offer the same choice twice for ever after.
 */
const inOrder = computed(() => [...new Set(ROWS.flatMap(row => row.labels))].filter(label => picked.value.has(label)))

function toggle(label: string) {
  const next = new Set(picked.value)
  if (next.has(label)) next.delete(label)
  else next.add(label)
  picked.value = next
}

/** Dragging adds rather than toggles, so crossing the same box twice does not undo it. */
function touch(label: string) {
  if (!dragging.value || picked.value.has(label)) return
  picked.value = new Set(picked.value).add(label)
}

function startDrag(label: string) {
  dragging.value = true
  touch(label)
}

function stopDrag() {
  dragging.value = false
}

window.addEventListener('mouseup', stopDrag)
onBeforeUnmount(() => window.removeEventListener('mouseup', stopDrag))

function confirm() {
  if (inOrder.value.length === 0) return
  emit('add', inOrder.value)
  picked.value = new Set()
}
</script>

<template>
  <div
      class="space-y-2 rounded-lg border border-(--border) bg-(--bg) p-3 shadow-lg"
      data-testid="size-quick-pick"
      @mouseleave="stopDrag"
  >
    <MutedText size="sm" tag="p">{{ t('inventory.manage.quickSizesHint') }}</MutedText>

    <div v-for="row in ROWS" :key="row.key" class="flex flex-wrap gap-1">
      <button
          v-for="label in row.labels"
          :key="label"
          type="button"
          class="min-w-10 cursor-pointer rounded-md border px-2 py-1 text-xs select-none"
          :class="picked.has(label)
              ? 'border-primary bg-primary text-white'
              : 'border-(--border) hover:bg-(--bg-accent)'"
          :data-testid="`size-box-${label}`"
          @mousedown.prevent="startDrag(label)"
          @mouseenter="touch(label)"
          @click="dragging || toggle(label)"
      >
        {{ label }}
      </button>
    </div>

    <div class="flex items-center justify-between gap-2 pt-1">
      <MutedText size="sm" tag="span">{{ t('inventory.manage.quickSizesPicked', {count: inOrder.length}) }}</MutedText>
      <div class="flex gap-2">
        <SecondaryButton @click="emit('close')">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="inOrder.length === 0" data-testid="size-quick-add" @click="confirm">
          {{ t('inventory.manage.quickSizesAdd') }}
        </PrimaryButton>
      </div>
    </div>
  </div>
</template>
