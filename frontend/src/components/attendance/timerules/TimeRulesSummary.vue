/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import type {TimeRule} from '@/api/generated/schema'
import {extraTimeText} from './extraTimeText'

/**
 * The preparation and follow-up rules of a sheet for somebody who may read them but not change them,
 * such as on a closed sheet: what each rule is called and what it credits.
 */
defineProps<{
  rules: TimeRule[]
}>()

const {t} = useI18n()
</script>

<template>
  <MutedText v-if="rules.length === 0" tag="p" size="sm">{{ t('attendanceTimeRules.empty') }}</MutedText>
  <ul v-else class="space-y-1 text-sm" data-testid="time-rules-summary">
    <li v-for="rule in rules" :key="rule.id" class="flex flex-wrap items-center gap-2">
      <span class="font-medium">{{ rule.label || t('attendanceTimeRules.unnamed') }}</span>
      <span class="text-(--text-muted)">{{ extraTimeText(t, rule.preparationMinutes, rule.followUpMinutes) }}</span>
      <ErrorBadge v-if="!rule.intact">{{ t('attendanceTimeRules.lostPartShort') }}</ErrorBadge>
    </li>
  </ul>
</template>
