/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MailProviderCredentialFields from '@/components/mail/MailProviderCredentialFields.vue'
import MailingTextField from './MailingTextField.vue'
import MailingNumberField from './MailingNumberField.vue'
import type {MailingConfig} from '@/api/adminSettings'

const {t} = useI18n()

const mailingConfig = defineModel<MailingConfig>({required: true})

const mailProviders = ['NONE', 'SMTP', 'RAPIDMAIL', 'TWILIO', 'SWEEGO', 'BREVO']

const provider = computed(() => mailingConfig.value.provider)
const configured = computed(() => provider.value !== 'NONE')
const isRelayProvider = computed(() => ['RAPIDMAIL', 'TWILIO', 'SWEEGO', 'BREVO'].includes(provider.value))
</script>

<template>
  <div class="space-y-4">
    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <div>
        <FieldLabel class="mb-1">{{ t('adminSettings.mailing.provider') }}</FieldLabel>
        <SelectInput v-model="mailingConfig.provider" class="w-full">
          <option v-for="p in mailProviders" :key="p" :value="p">{{ p }}</option>
        </SelectInput>
      </div>
    </div>

    <template v-if="configured">
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <MailingTextField v-model="mailingConfig.senderAddress" :label="t('adminSettings.mailing.senderAddress')"/>
        <MailingTextField v-model="mailingConfig.senderName" :label="t('adminSettings.mailing.senderName')"/>
        <MailingTextField
            v-if="provider === 'SMTP'"
            v-model="mailingConfig.user"
            :label="t('adminSettings.mailing.user')"
        />
        <MailingTextField
            v-if="provider === 'SMTP'"
            v-model="mailingConfig.password"
            :label="t('adminSettings.mailing.password')"
            type="password"
        />
        <MailingNumberField
            v-model="mailingConfig.dailySendLimit"
            :label="t('adminSettings.mailing.dailySendLimit')"
            :hint="t('adminSettings.mailing.dailySendLimitHint')"
        />
        <MailingNumberField
            v-model="mailingConfig.notificationDigestIntervalMinutes"
            :label="t('adminSettings.mailing.notificationDigestIntervalMinutes')"
            :hint="t('adminSettings.mailing.notificationDigestIntervalMinutesHint')"
        />
      </div>

      <MailProviderCredentialFields
          v-if="isRelayProvider"
          v-model:user="mailingConfig.user"
          v-model:secret="mailingConfig.apiKey"
          :provider="provider"
      />
    </template>
  </div>
</template>
