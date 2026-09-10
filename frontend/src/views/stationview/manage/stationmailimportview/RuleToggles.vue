/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * The four choices a rule makes that are each worth a sentence: two of them are off by default because
 * being on quietly does the wrong thing.
 */
const readSubjectForMember = defineModel<boolean>('readSubjectForMember', {required: true})
const includeInline = defineModel<boolean>('includeInline', {required: true})
const hidden = defineModel<boolean>('hidden', {required: true})
const keepOnArchive = defineModel<boolean>('keepOnArchive', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-2">
    <div class="flex items-center gap-2">
      <ToggleInput v-model="readSubjectForMember" data-testid="rule-read-subject"/>
      <span class="text-sm">{{ t('mailImport.rule.readSubjectForMember') }}</span>
    </div>
    <MutedText size="sm" tag="p">{{ t('mailImport.rule.readSubjectHint') }}</MutedText>
    <div class="flex items-center gap-2">
      <ToggleInput v-model="includeInline" data-testid="rule-include-inline"/>
      <span class="text-sm">{{ t('mailImport.rule.includeInline') }}</span>
    </div>
    <MutedText size="sm" tag="p">{{ t('mailImport.rule.includeInlineHint') }}</MutedText>
    <div class="flex items-center gap-2">
      <ToggleInput v-model="hidden" data-testid="rule-hidden"/>
      <span class="text-sm">{{ t('mailImport.rule.hiddenLabel') }}</span>
    </div>
    <div class="flex items-center gap-2">
      <ToggleInput v-model="keepOnArchive" data-testid="rule-keep"/>
      <span class="text-sm">{{ t('mailImport.rule.keepOnArchive') }}</span>
    </div>
  </div>
</template>
