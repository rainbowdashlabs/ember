/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onBeforeUnmount, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import {DRAG_CONTROL_COLUMN} from '../dragControls'

/**
 * One row of a sortable list, with the controls that move it.
 *
 * <p>Where the row is tall enough to hold them, the controls stand one above the other and take the
 * whole height: up at the top, down at the bottom, the grip between them. That is where the eye
 * expects them on a card, and it makes each of them a larger thing to hit. A row of one line has no
 * such space, so there they sit side by side.
 *
 * <p>What decides it is the height of the row's own content, measured rather than declared: the same
 * list holds a one-line entry and one that grew a description, and each should be laid out for what
 * it actually is. Measuring the content rather than the whole row is what keeps that from swinging
 * back and forth, because the controls are never what makes a tall row tall.
 */
const props = defineProps<{
  index: number
  total: number
  dragging: boolean
  disabled?: boolean
  finePointer: boolean
}>()

const emit = defineEmits<{
  move: [index: number, direction: -1 | 1]
  grab: [index: number, event: DragEvent]
}>()

const {t} = useI18n()

/** Room for three controls above each other, with space between them. */
const STACKED_FROM = 88

const content = ref<HTMLElement | null>(null)
const stacked = ref(false)

let observer: ResizeObserver | null = null

onMounted(() => {
  if (!content.value || typeof ResizeObserver === 'undefined') return
  observer = new ResizeObserver(entries => {
    const height = entries[0]?.contentRect.height ?? 0
    stacked.value = height >= STACKED_FROM
  })
  observer.observe(content.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})
</script>

<template>
  <div :class="['flex gap-2', stacked ? 'items-stretch' : 'items-center', dragging ? 'opacity-40' : '']"
       data-drag-row>
    <div
        v-if="!disabled"
        :class="stacked
          ? 'flex w-8 shrink-0 flex-col items-center justify-between py-1'
          : [DRAG_CONTROL_COLUMN, 'flex shrink-0 items-center justify-end gap-0.5']"
        :data-layout="stacked ? 'stacked' : 'inline'"
        data-testid="drag-controls"
    >
      <MutedIconButton
          :disabled="props.index === 0"
          :icon="['fas', 'arrow-up']"
          :label="t('common.moveUp')"
          class="!p-1"
          data-testid="move-up"
          @click="emit('move', props.index, -1)"
      />
      <span
          v-if="finePointer"
          class="cursor-grab px-1 text-(--text-muted) active:cursor-grabbing"
          data-testid="drag-handle"
          draggable="true"
          @dragstart="emit('grab', props.index, $event)"
      >
        <font-awesome-icon :icon="['fas', 'grip-vertical']" class="h-4 w-4"/>
      </span>
      <MutedIconButton
          :disabled="props.index === props.total - 1"
          :icon="['fas', 'arrow-down']"
          :label="t('common.moveDown')"
          class="!p-1"
          data-testid="move-down"
          @click="emit('move', props.index, 1)"
      />
    </div>

    <div ref="content" class="min-w-0 flex-1">
      <slot/>
    </div>
  </div>
</template>
