/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const props = defineProps<{
  label: string
  hint: string
  url: string
  copied: string
}>()

const emit = defineEmits<{
  (e: 'copy', url: string): void
}>()

function onCopy() {
  emit('copy', props.url)
}
</script>

<template>
  <details class="space-y-1">
    <summary class="cursor-pointer text-sm text-(--text-muted) hover:text-(--text) transition-colors">
      {{ label }}
    </summary>
    <div class="space-y-1 pt-2">
      <MutedText tag="div" size="sm">{{ hint }}</MutedText>
      <div class="flex items-center gap-2">
        <code class="flex-1 rounded bg-bg-light-accent dark:bg-bg-dark-accent px-3 py-2 text-xs break-all select-all">{{ url }}</code>
        <SecondaryButton @click="onCopy">
          <font-awesome-icon :icon="copied === url ? ['fas', 'check'] : ['fas', 'copy']"/>
        </SecondaryButton>
      </div>
    </div>
  </details>
</template>
