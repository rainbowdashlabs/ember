/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {LegalImport} from '@/api/adminSettings'

/** What the import found, shown before anything is applied. */
defineProps<{
  result: LegalImport
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex flex-wrap items-baseline gap-x-4 gap-y-1">
      <FieldLabel>{{ result.title || t('adminSettings.legal.importNoTitle') }}</FieldLabel>
      <MutedText size="sm">
        {{ t('adminSettings.legal.importFound', {sections: result.files.length, references: result.references}) }}
      </MutedText>
    </div>

    <ul class="max-h-48 space-y-1 overflow-auto">
      <li v-for="file in result.files" :key="file.filename" class="font-mono text-sm">{{ file.filename }}</li>
    </ul>

    <InfoContainer v-if="result.unmatched.length > 0" class="space-y-1">
      <FieldLabel>{{ t('adminSettings.legal.importUnmatched') }}</FieldLabel>
      <MutedText size="sm">{{ t('adminSettings.legal.importUnmatchedHint') }}</MutedText>
      <p class="font-mono text-sm">{{ result.unmatched.join(' · ') }}</p>
    </InfoContainer>
  </NeutralContainer>
</template>
