/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SectionLabel from '@/components/typography/SectionLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {RuleMember, RulePreview} from '@/api/generated/schema'

/** Who a rule matches today, and who it would take in and let go compared with the members now. */
defineProps<{
  preview: RulePreview
}>()

const {t} = useI18n()

function names(members: readonly RuleMember[]): string {
  return members.map(member => member.name).join(', ')
}
</script>

<template>
  <div class="space-y-2">
    <p class="font-medium">{{ t('memberRules.preview.matching', {count: preview.matching.length}) }}</p>
    <div>
      <SectionLabel>{{ t('memberRules.preview.joining') }}</SectionLabel>
      <MutedText tag="p">{{ preview.joining.length > 0 ? names(preview.joining) : t('memberRules.preview.nobody') }}</MutedText>
    </div>
    <div>
      <SectionLabel>{{ t('memberRules.preview.leaving') }}</SectionLabel>
      <MutedText tag="p">{{ preview.leaving.length > 0 ? names(preview.leaving) : t('memberRules.preview.nobody') }}</MutedText>
    </div>
  </div>
</template>
