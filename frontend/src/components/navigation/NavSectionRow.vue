/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import CountBadge from '@/components/badge/CountBadge.vue'
import type {NavGroup} from '@/data/sidebar/navTypes'

/**
 * A section in the full list of the sidebar: a row with an arrow that opens the section. It is lit when
 * the page shown is in it, so the reader sees where they are without opening anything.
 *
 * <p>The row is a real link to the section's first page, so a middle click or a modified click opens that
 * page in a new tab the way any other link does. Only a plain click opens the section here.
 */
const props = defineProps<{
  section: NavGroup
  active: boolean
}>()

const emit = defineEmits<{
  (e: 'open'): void
}>()

/** Lets the router take a plain click and tells the sidebar, and leaves every other click to the browser. */
function follow(event: MouseEvent, navigate: (event?: MouseEvent) => unknown) {
  void navigate(event)
  if (event.defaultPrevented) emit('open')
}
</script>

<template>
  <router-link v-slot="{href, navigate}" :to="props.section.to" custom>
    <a :href="href" :data-section="section.key" :data-onboarding="section.onboarding"
       :data-onboarding-holds="section.holds.join(' ') || undefined" :data-active="active ? 'true' : undefined"
       :class="active ? '!text-primary' : '!text-[var(--text)] hover:bg-primary/5'"
       class="flex w-full items-center gap-3 rounded-theme px-3 py-2 text-sm font-medium no-underline transition-colors duration-150"
       @click="follow($event, navigate)">
      <font-awesome-icon :icon="['fas', section.icon]" class="w-4 shrink-0"/>
      <span class="flex-1 truncate text-left">{{ section.label }}</span>
      <CountBadge v-if="section.badge > 0" :count="section.badge"/>
      <font-awesome-icon :icon="['fas', 'chevron-right']" class="h-3 w-3 shrink-0"/>
    </a>
  </router-link>
</template>
