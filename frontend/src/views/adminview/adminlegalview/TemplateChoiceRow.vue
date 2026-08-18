/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import type {LegalTemplate} from '@/api/adminSettings'

/**
 * One section on offer, described well enough to choose it.
 *
 * The file name alone is not enough: five of the shipped sections are called something like
 * `05-mailversand-…`, and which one belongs in the document depends on what it says. So the row
 * leads with the heading the section carries, and its text can be read here rather than after
 * loading it and finding out.
 */
const props = defineProps<{
  template: LegalTemplate
  selected: boolean
  /** Whether a section of this name is already in the editor and would be replaced. */
  replaces: boolean
}>()

const emit = defineEmits<{
  toggle: []
}>()

const {t} = useI18n()

const expanded = ref(false)

/**
 * The first heading of the section, which is what a reader of the document sees. Falls back to the
 * file name for a section that opens with something else.
 */
const title = computed(() => {
  const heading = props.template.content.split('\n').find(line => line.trimStart().startsWith('#'))
  return heading ? heading.replace(/^\s*#+\s*/, '').trim() : props.template.displayName
})
</script>

<template>
  <div class="rounded-lg border border-(--border)">
    <div class="flex items-center gap-3 p-3">
      <ToggleInput :model-value="selected" :aria-label="title" @update:model-value="emit('toggle')"/>
      <div class="flex-1 min-w-0">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="font-medium truncate">{{ title }}</span>
          <InfoBadge v-if="replaces">{{ t('adminSettings.legal.templateReplaces') }}</InfoBadge>
          <SecondaryBadge v-if="template.optional">{{ t('adminSettings.legal.templateOptional') }}</SecondaryBadge>
        </div>
        <div class="font-mono text-xs text-(--text-muted) truncate">{{ template.displayName }}</div>
      </div>
      <MutedIconButton
          :icon="['fas', expanded ? 'chevron-up' : 'chevron-down']"
          :label="t('adminSettings.legal.templatePreview')"
          hover="text"
          @click="expanded = !expanded"/>
    </div>
    <pre v-if="expanded"
         class="max-h-64 overflow-auto border-t border-(--border) p-3 text-xs whitespace-pre-wrap">{{ template.content }}</pre>
  </div>
</template>
