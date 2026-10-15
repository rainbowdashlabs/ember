/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
/**
 * The two columns of messages: the conversations on the left, the open one on the right, both as
 * tall as the window leaves them. Below the large breakpoint only one of them is shown, the open
 * conversation where there is one and the list otherwise.
 */
defineProps<{
  /** Whether a conversation is open, which is what a phone shows instead of the list. */
  open: boolean
}>()
</script>

<template>
  <div class="flex h-[calc(100dvh-9rem)] min-h-96 overflow-hidden rounded-theme border border-(--border)">
    <aside :class="open ? 'hidden lg:flex' : 'flex'"
           class="w-full lg:w-80 shrink-0 flex-col border-r border-(--border) bg-bg-light-accent/40 dark:bg-bg-dark-accent/40">
      <slot name="list"/>
    </aside>
    <section :class="open ? 'flex' : 'hidden lg:flex'" class="min-w-0 flex-1 flex-col">
      <slot name="conversation"/>
    </section>
  </div>
</template>
