/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, useId, watch} from 'vue'
import AppIcon from '@/components/display/AppIcon.vue'
import BareButton from '@/components/button/BareButton.vue'

/**
 * A part of a long form that can be folded away, with a line showing what it holds while it is closed.
 *
 * <p>The essentials of a form stand open; everything else is a section like this, closed until it is
 * needed, so the form reads at a glance and still shows what is set. Its content stays rendered while
 * it is closed, only hidden, so what it holds keeps its state and is saved with the rest.
 *
 * <p>A section opens by itself as soon as it asks for attention, such as a field in it that has to be
 * corrected or a note about something that was not taken over. It never closes by itself.
 */
const props = defineProps<{
  title: string
  /** What the section holds, in one line, shown beside the title. */
  summary?: string
  /** Whether the section holds something the reader has to see, which opens it. */
  attention?: boolean
  /** The heading level of its title, so heading navigation finds the section; a third level by default. */
  level?: 2 | 3 | 4
}>()

const headingTag = computed(() => `h${props.level ?? 3}`)

const open = defineModel<boolean>('open', {default: false})

const contentId = useId()

watch(() => props.attention, needed => {
  if (needed) open.value = true
}, {immediate: true})
</script>

<template>
  <section class="space-y-3" :data-open="open">
    <component :is="headingTag" class="m-0">
      <BareButton
          class="flex w-full items-start gap-2 text-left"
          :aria-expanded="open"
          :aria-controls="contentId"
          data-section-toggle
          @click="open = !open"
      >
        <AppIcon :icon="['fas', open ? 'chevron-down' : 'chevron-right']" class="mt-1.5 h-3 w-3 shrink-0 text-(--text-muted)"/>
        <span class="flex min-w-0 flex-1 flex-wrap items-baseline gap-x-3 gap-y-0.5">
          <span class="text-base sm:text-lg font-semibold">{{ title }}</span>
          <span v-if="summary" class="text-sm text-(--text-muted)" data-section-summary>{{ summary }}</span>
        </span>
      </BareButton>
    </component>
    <div v-show="open" :id="contentId" class="space-y-4">
      <slot/>
    </div>
  </section>
</template>
