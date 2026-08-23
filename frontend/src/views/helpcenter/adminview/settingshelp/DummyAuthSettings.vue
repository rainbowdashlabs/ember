/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()

const fields = [
  {label: 'tokenBytes', hint: 'tokenBytesHint', value: 32},
  {label: 'verifyTokenHours', hint: 'verifyTokenHoursHint', value: 24},
  {label: 'passwordTokenHours', hint: 'passwordTokenHoursHint', value: 72},
  {label: 'sessionMinutes', hint: 'sessionMinutesHint', value: 43200},
]
</script>

<template>
  <NeutralContainer class="space-y-4 mt-4">
    <SectionHeader>{{ t('adminSettings.auth.title') }}</SectionHeader>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <div v-for="f in fields" :key="f.label">
        <FieldLabel class="mb-1">{{ t(`adminSettings.auth.${f.label}`) }}</FieldLabel>
        <NumberInput :model-value="f.value" />
        <MutedText tag="div" class="mt-1">{{ t(`adminSettings.auth.${f.hint}`) }}</MutedText>
      </div>
    </div>

    <div class="flex justify-end">
      <PrimaryButton>{{ t('adminSettings.legal.save') }}</PrimaryButton>
    </div>
  </NeutralContainer>
</template>
