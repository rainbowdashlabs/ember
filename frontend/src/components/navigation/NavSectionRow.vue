/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BareButton from '@/components/button/BareButton.vue'
import CountBadge from '@/components/badge/CountBadge.vue'
import type {NavGroup} from '@/data/sidebar/navTypes'

/**
 * A section in the full list of the sidebar: a row with an arrow that opens the section. It is lit when
 * the page shown is in it, so the reader sees where they are without opening anything.
 */
defineProps<{
  section: NavGroup
  active: boolean
}>()

defineEmits<{
  (e: 'open'): void
}>()
</script>

<template>
  <BareButton :data-section="section.key" :data-onboarding="section.onboarding"
              :data-onboarding-holds="section.holds.join(' ') || undefined" :data-active="active ? 'true' : undefined"
              :class="active ? '!text-primary' : '!text-[var(--text)] hover:bg-primary/5'"
              class="flex w-full items-center gap-3 rounded-theme px-3 py-2 text-sm font-medium transition-colors duration-150"
              @click="$emit('open')">
    <font-awesome-icon :icon="['fas', section.icon]" class="w-4 shrink-0"/>
    <span class="flex-1 truncate text-left">{{ section.label }}</span>
    <CountBadge v-if="section.badge > 0" :count="section.badge"/>
    <font-awesome-icon :icon="['fas', 'chevron-right']" class="h-3 w-3 shrink-0"/>
  </BareButton>
</template>
