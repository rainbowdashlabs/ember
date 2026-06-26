/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import HelpCenterHint from '@/components/help/HelpCenterHint.vue'

const props = withDefaults(defineProps<{
  icon: [string, string]
  title: string
  helpRouteName: string
  hint: string
  url: string
  copied: string
  recommended?: boolean
  recommendedLabel?: string
}>(), {
  recommended: false,
  recommendedLabel: '',
})

const emit = defineEmits<{
  (e: 'copy', url: string): void
}>()

const {t} = useI18n()

function onCopy() {
  emit('copy', props.url)
}
</script>

<template>
  <div class="space-y-1 rounded-theme border border-primary/40 bg-primary/5 p-3">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div class="flex items-center gap-2">
        <font-awesome-icon :icon="icon" class="text-primary"/>
        <FieldLabel>{{ title }}</FieldLabel>
        <PrimaryBadge v-if="recommended">{{ recommendedLabel }}</PrimaryBadge>
      </div>
      <HelpCenterHint :to="{name: helpRouteName}">
        {{ t('userSettings.feedHelp') }}
      </HelpCenterHint>
    </div>
    <MutedText tag="div" size="sm">{{ hint }}</MutedText>
    <div class="flex items-center gap-2">
      <code class="flex-1 rounded bg-bg-light dark:bg-bg-dark px-3 py-2 text-xs break-all select-all">{{ url }}</code>
      <SecondaryButton @click="onCopy">
        <font-awesome-icon :icon="copied === url ? ['fas', 'check'] : ['fas', 'copy']"/>
      </SecondaryButton>
    </div>
  </div>
</template>
