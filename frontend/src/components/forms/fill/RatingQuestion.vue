/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import IconButton from '@/components/button/IconButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'

type RatingAnswer = { rating: number }

const props = defineProps<{
  config: Record<string, unknown>
}>()

const answer = defineModel<RatingAnswer>({ required: true })

const scale = computed(() => (props.config.scale as number) || 5)
const iconKind = computed(() => (props.config.icon as string) || 'STAR')

const ratingIcon = computed<string[]>(() => {
  if (iconKind.value === 'HEART') return ['fas', 'heart']
  if (iconKind.value === 'THUMB_UP') return ['fas', 'thumbs-up']
  return ['fas', 'star']
})

function setRating(value: number) {
  answer.value.rating = value
}
</script>

<template>
  <div class="flex flex-wrap gap-1">
    <template v-if="iconKind !== 'NUMBER'">
      <IconButton v-for="n in scale" :key="n"
              :icon="ratingIcon"
              :label="`Rating ${n}`"
              :class="n <= (answer.rating ?? 0) ? 'text-primary' : 'text-(--text-muted)'"
              class="text-2xl sm:text-xl hover:text-primary p-1"
              @click="setRating(n)" />
    </template>
    <template v-else>
      <SelectionToggleButton v-for="n in scale" :key="n"
              :selected="n <= (answer.rating ?? 0)"
              size="md"
              @toggle="setRating(n)">
        {{ n }}
      </SelectionToggleButton>
    </template>
  </div>
</template>
