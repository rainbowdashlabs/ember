/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SectionLabel from '@/components/typography/SectionLabel.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import AppIcon from '@/components/display/AppIcon.vue'
import PageMenu from './PageMenu.vue'
import type { PageDraft } from '../types'

/**
 * The top of a page in the editor: its number, its title and its description, and the menu for the
 * page as a whole.
 *
 * <p>A page that no path leads to is marked. That comes of changing where pages lead, and nobody
 * would otherwise notice the questions on it are never asked.
 */
defineProps<{
  page: PageDraft
  index: number
  last: boolean
  /** Whether some path through the form reaches this page. */
  reached: boolean
}>()

const emit = defineEmits<{
  move: [direction: -1 | 1]
  remove: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-2">
    <div class="flex items-center justify-between gap-2">
      <div class="flex items-center gap-2">
        <AppIcon :icon="['fas', 'grip-vertical']" data-page-grip :title="t('forms.pages.drag')"
                 class="cursor-grab text-(--text-muted) active:cursor-grabbing"/>
        <SectionLabel>{{ t('forms.pages.number', {number: index + 1}) }}</SectionLabel>
        <ErrorBadge v-if="!reached" data-testid="page-unreached">{{ t('forms.pages.unreached') }}</ErrorBadge>
      </div>
      <PageMenu :first="index === 0" :last="last" @move="direction => emit('move', direction)" @remove="emit('remove')"/>
    </div>
    <TextInput v-model="page.title" :placeholder="t('forms.pages.title')"/>
    <TextAreaInput v-model="page.description" :rows="2" :placeholder="t('forms.pages.description')"/>
  </div>
</template>
