/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useEventListener} from '@vueuse/core'
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import BareButton from '@/components/button/BareButton.vue'
import CountBadge from '@/components/badge/CountBadge.vue'
import NavEntry from '@/components/navigation/NavEntry.vue'
import type {NavGroup} from '@/data/sidebar/navTypes'
import {listed} from '@/util/navTree'

/**
 * One open section in place of the whole sidebar: the way back to all sections, the section's name as
 * a heading, then its pages and subgroups. Escape goes back like the button does.
 */
const props = defineProps<{
  section: NavGroup
}>()

const emit = defineEmits<{
  (e: 'back' | 'navigate'): void
}>()

const {t} = useI18n()

const children = computed(() => listed(props.section.children))

const root = ref<HTMLElement | null>(null)

useEventListener(root, 'keydown', (event: KeyboardEvent) => {
  if (event.key !== 'Escape') return
  event.stopPropagation()
  emit('back')
})
</script>

<template>
  <div ref="root" class="flex flex-col gap-1">
    <BareButton data-back data-onboarding="nav" class="flex w-full items-center gap-2 rounded-theme px-3 py-1.5 text-xs font-medium text-[var(--text-muted)] transition-colors duration-150 hover:text-[var(--text)]"
                @click="emit('back')">
      <font-awesome-icon :icon="['fas', 'arrow-left']" class="h-3 w-3"/>
      {{ t('sidebar.allSections') }}
    </BareButton>
    <div data-active="true" class="flex items-center gap-3 px-3 pb-1 pt-2 text-sm font-semibold text-primary">
      <font-awesome-icon :icon="['fas', section.icon]" class="w-4 shrink-0"/>
      <span class="flex-1 truncate">{{ section.label }}</span>
      <CountBadge v-if="section.badge > 0" :count="section.badge"/>
    </div>
    <NavEntry v-for="child in children" :key="child.key" :node="child" nested @navigate="emit('navigate')"/>
  </div>
</template>
