/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import GeneralFieldsPanel from './GeneralFieldsPanel.vue'
import SmtpPanel from './SmtpPanel.vue'
import type {MailingConfig} from '@/api/adminSettings'
import {needsServerAddress} from '@/util/mailProviders'

/**
 * The provider the instance itself sends through, with the actions that belong to it: try it,
 * clear it, save it.
 */
const config = defineModel<MailingConfig>({required: true})

defineProps<{
  clearing: boolean
  sendingTestMail: boolean
  testMailSent: boolean
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
    <GeneralFieldsPanel v-model="config"/>
    <SmtpPanel v-if="needsServerAddress(config.provider)" v-model="config"/>
    <div class="flex justify-end gap-2 flex-wrap">
      <ErrorButton :icon="['fas', 'trash']" :disabled="clearing" @click="emit('clear')">
        {{ t('adminSettings.mailing.clear') }}
      </ErrorButton>
      <SecondaryButton
          v-if="config.provider !== 'NONE'"
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
