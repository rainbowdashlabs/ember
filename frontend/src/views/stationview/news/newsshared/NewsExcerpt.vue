/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ProseContent from '@/components/display/ProseContent.vue'

/**
 * As much of an entry as a list is willing to show.
 *
 * <p>A list of entries printed whole is not a list: one long entry pushes every other one off the
 * screen, and somebody looking for a particular entry scrolls past an article to reach the next
 * headline. Cut to a few lines, the list reads as a list again, and the entry's own page becomes the
 * place the rest of it lives rather than a second copy of what was already on screen.
 *
 * <p>Whether it was cut is measured rather than guessed: the mark and the invitation to read on are
 * shown only where something is actually hidden, so a two line entry does not pretend to continue.
 */
const props = defineProps<{contentHtml?: string}>()

const {t} = useI18n()
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

watch(() => props.contentHtml, () => nextTick(measure))
</script>

<template>
  <div v-if="contentHtml" class="space-y-1">
    <div ref="body" :class="{'news-excerpt-cut': cut}" class="max-h-44 overflow-hidden">
      <ProseContent v-html="contentHtml"/>
    </div>
    <p v-if="cut" class="text-xs text-primary">{{ t('news.readMore') }}</p>
  </div>
</template>

<style scoped>
.news-excerpt-cut {
    mask-image: linear-gradient(to bottom, black 60%, transparent 100%);
    -webkit-mask-image: linear-gradient(to bottom, black 60%, transparent 100%);
}
</style>
