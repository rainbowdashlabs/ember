/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NumberSetting from '@/views/adminview/adminsecuritytokensview/NumberSetting.vue'
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
      <NumberSetting
          v-model="config.tokenBytes"
          :label="t('adminSecurity.tokens.tokenBytes')"
          :hint="t('adminSecurity.tokens.tokenBytesHint')"/>
      <NumberSetting
          v-model="config.sessionMinutes"
          :label="t('adminSecurity.tokens.sessionMinutes')"
          :hint="t('adminSecurity.tokens.sessionMinutesHint')"/>
      <NumberSetting
          v-model="config.untrustedSessionMinutes"
          :label="t('adminSecurity.tokens.untrustedSessionMinutes')"
          :hint="t('adminSecurity.tokens.untrustedSessionMinutesHint')"/>
      <NumberSetting
          v-model="config.verifyTokenHours"
          :label="t('adminSecurity.tokens.verifyTokenHours')"
          :hint="t('adminSecurity.tokens.verifyTokenHoursHint')"/>
      <NumberSetting
          v-model="config.passwordTokenHours"
          :label="t('adminSecurity.tokens.passwordTokenHours')"
          :hint="t('adminSecurity.tokens.passwordTokenHoursHint')"/>
      <NumberSetting
          v-model="config.setupTokenDays"
          :label="t('adminSecurity.tokens.setupTokenDays')"
          :hint="t('adminSecurity.tokens.setupTokenDaysHint')" data-testid="setup-token-days"/>
    </div>

    <div class="flex justify-end">
      <SaveButton :action="save"/>
    </div>
  </NeutralContainer>
</template>
