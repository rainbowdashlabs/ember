/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import IconButton from '@/components/button/IconButton.vue'
import ThemeToggle from '@/components/theme/ThemeToggle.vue'

defineProps<{
  /** Set while nothing has happened for a while; the bar fades out of the way. */
  idle?: boolean
  column: number
  columns: number
  row: number
  rows: number
}>()

const emit = defineEmits<{
  left: []
  right: []
  up: []
  down: []
  fullscreen: []
}>()
</script>

<template>
  <div class="fixed bottom-4 right-4 z-40 flex items-center gap-1 rounded-full border border-bg-light-accent
              bg-(--bg) px-2 py-1 shadow-lg transition-opacity duration-500 dark:border-bg-dark-accent"
       :class="idle ? 'pointer-events-none opacity-0' : 'opacity-100'">
    <IconButton :icon="['fas', 'chevron-left']" label="Vorheriges Thema"
                :disabled="column <= 1" @click="emit('left')"/>
    <span class="min-w-12 text-center text-xs font-semibold tabular-nums">{{ column }} / {{ columns }}</span>
    <IconButton :icon="['fas', 'chevron-right']" label="Nächstes Thema"
                :disabled="column >= columns" @click="emit('right')"/>

    <span class="mx-1 h-5 w-px bg-(--bg-accent)"/>

    <IconButton :icon="['fas', 'chevron-up']" label="Zurück aus der Tiefe"
                :disabled="row <= 1" @click="emit('up')"/>
    <span class="min-w-10 text-center text-xs tabular-nums text-(--text-muted)">{{ row }} / {{ rows }}</span>
    <IconButton :icon="['fas', 'chevron-down']" label="Details zeigen"
                :disabled="row >= rows" @click="emit('down')"/>

    <span class="mx-1 h-5 w-px bg-(--bg-accent)"/>

    <ThemeToggle/>
    <IconButton :icon="['fas', 'expand']" label="Vollbild" @click="emit('fullscreen')"/>
  </div>
</template>
