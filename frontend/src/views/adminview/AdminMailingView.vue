/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import GeneralFieldsPanel from './adminmailingview/GeneralFieldsPanel.vue'
import SmtpPanel from './adminmailingview/SmtpPanel.vue'
import {adminSettings} from '@/api'
import type {MailingConfig} from '@/api/adminSettings'
import {useConfigPanel} from '@/composables/useConfigPanel'

const {t} = useI18n()

const {config: mailingConfig, loading, error, runWith} = useConfigPanel<MailingConfig>({
  initial: {
    provider: 'SMTP',
    senderAddress: '',
    senderName: '',
    user: '',
    password: '',
    apiKey: '',
    smtpHost: '',
    smtpPort: 665,
    smtpSsl: false,
    dailySendLimit: 200,
    notificationDigestIntervalMinutes: 60,
  },
  fetch: () => adminSettings.getMailingConfig(),
})

async function saveMailingConfig() {
  await runWith(() => adminSettings.updateMailingConfig(mailingConfig.value), {rethrow: true})
}
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('adminSettings.mailing.title') }}</SectionHeader>
          <GeneralFieldsPanel v-model="mailingConfig"/>
          <SmtpPanel v-model="mailingConfig"/>
          <div class="flex justify-end">
            <SaveButton :action="saveMailingConfig"/>
          </div>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
