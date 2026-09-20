/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import ProseContent from './ProseContent.vue'

/**
 * As much of a body of rich text as a list is willing to show.
 *
 * <p>A list whose entries are printed whole is not a list: one long entry pushes every other one
 * off the screen, and somebody looking for a particular one scrolls past an article to reach the
 * next headline. Cut to a few lines, the list reads as a list again, and wherever the entry leads
 * becomes the place the rest of it lives.
 *
 * <p>Whether it was cut is measured rather than guessed, so the fade and the invitation to read on
 * appear only where something is actually hidden: a short entry does not pretend to continue.
 *
 * <p>Cut by height rather than by a count of lines, because a body holds paragraphs, lists and
 * headings rather than one run of text, and clamping lines only ever reaches the first of them.
 */
const props = withDefaults(defineProps<{
  /** The body, already rendered to HTML and sanitised. */
  html?: string
  /** How tall it may grow before it is cut, as the height class to apply. */
  maxHeight?: string
  /** What to say under a cut body, for a list that leads somewhere to read the rest. */
  moreLabel?: string
}>(), {html: '', maxHeight: 'max-h-44', moreLabel: ''})

const body = ref<HTMLElement | null>(null)
const cut = ref(false)

function measure() {
  const element = body.value
  cut.value = !!element && element.scrollHeight > element.clientHeight + 1
}

let observer: ResizeObserver | null = null

onMounted(() => {
  measure()
  if (typeof ResizeObserver === 'undefined' || !body.value) return
  observer = new ResizeObserver(measure)
  observer.observe(body.value)
})

onBeforeUnmount(() => observer?.disconnect())

watch(() => props.html, () => nextTick(measure))
</script>

<template>
  <div v-if="html" class="space-y-1">
    <div ref="body" :class="[maxHeight, {'prose-excerpt-cut': cut}]" class="overflow-hidden">
      <ProseContent v-html="html"/>
    </div>
    <p v-if="cut && moreLabel" class="text-xs text-primary">{{ moreLabel }}</p>
  </div>
</template>

<style scoped>
.prose-excerpt-cut {
    mask-image: linear-gradient(to bottom, black 60%, transparent 100%);
    -webkit-mask-image: linear-gradient(to bottom, black 60%, transparent 100%);
}
</style>
