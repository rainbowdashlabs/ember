/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'

/**
 * How one line of stock stands, as three shares of a single strip.
 *
 * <p>The three are what is free, what a member has, and what is neither: gear out on loan and gear
 * nobody can find. They are read off the numbers the line already carries rather than counted a
 * second time, and the third is what the other two leave over, so the strip is always exactly the
 * whole line and never claims a piece twice or loses one.
 *
 * <p>A line with nothing on it draws no share at all. A full strip and an empty strip both say
 * something about a stock that exists, and a size nobody keeps says neither.
 *
 * <p>The three counts stand in the label as well as in the colours, because a strip read by colour
 * alone says nothing to a reader who cannot tell the colours apart.
 */
const props = defineProps<{
  total: number
  free: number
  assigned: number
}>()

const {t} = useI18n()

const away = computed(() => Math.max(0, props.total - props.free - props.assigned))

function share(count: number): string {
  return `${(count / props.total) * 100}%`
}

const label = computed(() => props.total === 0
    ? t('inventory.detail.shareBarEmpty')
    : t('inventory.detail.shareBarLabel', {
      free: props.free,
      assigned: props.assigned,
      away: away.value,
    }))
</script>

<template>
  <div
      :aria-label="label"
      :title="label"
      class="mt-1 flex h-1 w-full min-w-8 overflow-hidden rounded-full bg-(--bg-accent)"
      data-testid="size-share-bar"
      role="img"
  >
    <template v-if="props.total > 0">
      <div
          v-if="props.free > 0"
          :style="{width: share(props.free)}"
          class="h-full bg-success"
          data-testid="size-share-free"
      />
      <div
          v-if="props.assigned > 0"
          :style="{width: share(props.assigned)}"
          class="h-full bg-primary"
          data-testid="size-share-assigned"
      />
      <div
          v-if="away > 0"
          :style="{width: share(away)}"
          class="h-full bg-error"
          data-testid="size-share-away"
      />
    </template>
  </div>
</template>
