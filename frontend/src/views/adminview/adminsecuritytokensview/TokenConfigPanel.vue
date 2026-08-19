/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import type {TokensConfigResponse} from '@/api/adminSettings'

const {t} = useI18n()

defineProps<{
  config: TokensConfigResponse
  save: () => Promise<void>
}>()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('adminSecurity.tokens.title') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('adminSecurity.tokens.hint') }}</MutedText>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <div>
        <FieldLabel class="mb-1">{{ t('adminSecurity.tokens.tokenBytes') }}</FieldLabel>
        <NumberInput v-model="config.tokenBytes"/>
        <MutedText class="mt-1" size="sm" tag="div">{{ t('adminSecurity.tokens.tokenBytesHint') }}</MutedText>
      </div>
      <div>
        <FieldLabel class="mb-1">{{ t('adminSecurity.tokens.sessionMinutes') }}</FieldLabel>
        <NumberInput v-model="config.sessionMinutes"/>
        <MutedText class="mt-1" size="sm" tag="div">{{ t('adminSecurity.tokens.sessionMinutesHint') }}</MutedText>
      </div>
      <div>
        <FieldLabel class="mb-1">{{ t('adminSecurity.tokens.untrustedSessionMinutes') }}</FieldLabel>
        <NumberInput v-model="config.untrustedSessionMinutes"/>
        <MutedText class="mt-1" size="sm" tag="div">
          {{ t('adminSecurity.tokens.untrustedSessionMinutesHint') }}
        </MutedText>
      </div>
      <div>
        <FieldLabel class="mb-1">{{ t('adminSecurity.tokens.verifyTokenHours') }}</FieldLabel>
        <NumberInput v-model="config.verifyTokenHours"/>
        <MutedText class="mt-1" size="sm" tag="div">{{ t('adminSecurity.tokens.verifyTokenHoursHint') }}</MutedText>
      </div>
      <div>
        <FieldLabel class="mb-1">{{ t('adminSecurity.tokens.passwordTokenHours') }}</FieldLabel>
        <NumberInput v-model="config.passwordTokenHours"/>
        <MutedText class="mt-1" size="sm" tag="div">{{ t('adminSecurity.tokens.passwordTokenHoursHint') }}</MutedText>
      </div>
    </div>

    <div class="flex justify-end">
      <SaveButton :action="save"/>
    </div>
  </NeutralContainer>
</template>
