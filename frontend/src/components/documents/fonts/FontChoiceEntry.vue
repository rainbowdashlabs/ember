/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FontSample from './FontSample.vue'
import type {FontEntry} from './fontOptions'

/**
 * One font of a font list: its name, where it comes from and a line of sample text in it. A family the
 * template names but no longer reaches has no sample; it is marked, and the note says what prints
 * instead.
 */
defineProps<{
  entry: FontEntry
  /** What prints in place of a family that is no longer reached. */
  missingNote: string
}>()

const {t} = useI18n()
</script>

<template>
  <span class="flex min-w-0 flex-1 flex-col gap-1">
    <span class="flex min-w-0 flex-wrap items-center gap-2">
      <span class="truncate font-medium">{{ entry.name }}</span>
      <SecondaryBadge v-if="entry.origin">{{ t(`documentFonts.origin.${entry.origin}`) }}</SecondaryBadge>
      <ErrorBadge v-if="entry.missing">{{ t('documentFonts.missing') }}</ErrorBadge>
    </span>
    <FontSample v-if="entry.sample" :family="entry.sample.family" :version="entry.sample.version"
                :label="t('documentFonts.sampleOf', {family: entry.name})"/>
    <MutedText v-if="entry.missing" size="xs">{{ missingNote }}</MutedText>
  </span>
</template>
