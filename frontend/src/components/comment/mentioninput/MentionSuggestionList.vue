/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import type {Suggestion} from '@/composables/useMentionQuery'
import MentionMemberOption from './MentionMemberOption.vue'
import MentionGroupOption from './MentionGroupOption.vue'
import MentionSpecialOption from './MentionSpecialOption.vue'

defineProps<{
  suggestions: Suggestion[]
  selectedIndex: number
  top: number
  left: number
}>()

const emit = defineEmits<{
  select: [suggestion: Suggestion]
  hover: [index: number]
}>()

function suggestionKey(s: Suggestion): string {
  if (s.kind === 'member') return `m-${s.data.id}`
  if (s.kind === 'group') return `g-${s.data.id}`
  return `s-${s.data.type}-${s.data.entityId}`
}
</script>

<template>
  <div
      :style="{ top: top + 'px', left: left + 'px' }"
      class="absolute z-30 w-72 max-h-52 overflow-y-auto rounded-theme border border-bg-light-accent bg-bg-light shadow-lg dark:border-bg-dark-accent dark:bg-bg-dark"
  >
    <button
        v-for="(s, i) in suggestions"
        :key="suggestionKey(s)"
        type="button"
        class="w-full px-3 py-1.5 text-left text-sm transition-colors flex items-center gap-2"
        :class="i === selectedIndex ? 'bg-primary/15 text-primary' : 'hover:bg-primary/10'"
        @mousedown.prevent="emit('select', s)"
        @mouseenter="emit('hover', i)"
    >
      <MentionMemberOption v-if="s.kind === 'member'" :data="s.data" />
      <MentionGroupOption v-else-if="s.kind === 'group'" :data="s.data" />
      <MentionSpecialOption v-else :data="s.data" />
    </button>
  </div>
</template>
