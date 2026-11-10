/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * One way to start something, as a tile in a grid of such ways: a title, the button that starts it and
 * a line or two saying what it brings.
 *
 * <p>The whole tile answers a click. The button carries no handler of its own: its click, by mouse or
 * keyboard, rises to the tile, so the tile is reached with the keyboard once and starts once however it
 * is clicked. Lines beyond the hint go into the slot.
 */
defineProps<{
  /** What this way starts from. */
  title: string
  /** The words on the button. */
  buttonLabel: string
  /** One line on what this way brings. */
  hint?: string | null
}>()

const emit = defineEmits<{
  start: []
}>()
</script>

<template>
  <NeutralContainer class="cursor-pointer hover:ring-2 hover:ring-primary/30 transition-all" @click="emit('start')">
    <div class="space-y-2">
      <div class="flex items-center justify-between gap-2">
        <span class="font-medium">{{ title }}</span>
        <PrimaryButton :icon="['fas', 'plus']">{{ buttonLabel }}</PrimaryButton>
      </div>
      <MutedText v-if="hint" tag="p" size="sm">{{ hint }}</MutedText>
      <slot/>
    </div>
  </NeutralContainer>
</template>
