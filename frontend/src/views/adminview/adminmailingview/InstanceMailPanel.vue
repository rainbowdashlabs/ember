/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import MailingNumberField from './MailingNumberField.vue'
import type {MailingConfig} from '@/api/adminSettings'

/**
 * What is left of the mailing page once the providers became a list of their own: the settings
 * that belong to the instance rather than to any one provider, and the actions that act on all of
 * them at once.
 */
const config = defineModel<MailingConfig>({required: true})

defineProps<{
  clearing: boolean
  sendingTestMail: boolean
  testMailSent: boolean
  /** Whether anything is listed, so sending a test mail is offered only when it could arrive. */
  hasProvider: boolean
  save: () => Promise<unknown>
}>()

const emit = defineEmits<{
  clear: []
  'test-mail': []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('adminSettings.mailing.instanceTitle') }}</SectionHeader>
    <MailingNumberField
        v-model="config.notificationDigestIntervalMinutes"
        :label="t('adminSettings.mailing.digestInterval')"
        :hint="t('adminSettings.mailing.digestIntervalHint')"
    />
    <div class="flex justify-end gap-2 flex-wrap">
      <ErrorButton :icon="['fas', 'trash']" :disabled="clearing" @click="emit('clear')">
        {{ t('adminSettings.mailing.clear') }}
      </ErrorButton>
      <SecondaryButton
          v-if="hasProvider"
          :icon="['fas', 'paper-plane']"
          :disabled="sendingTestMail"
          @click="emit('test-mail')">
        {{ sendingTestMail ? t('common.loading') : t('adminSettings.mailing.testMail') }}
      </SecondaryButton>
      <SaveButton :action="save"/>
    </div>
    <Alert v-if="testMailSent" variant="success">{{ t('adminSettings.mailing.testMailSent') }}</Alert>
  </NeutralContainer>
</template>
