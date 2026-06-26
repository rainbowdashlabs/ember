/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MailingTextField from './MailingTextField.vue'
import MailingNumberField from './MailingNumberField.vue'
import type {MailingConfig} from '@/api/adminSettings'

const {t} = useI18n()

const mailingConfig = defineModel<MailingConfig>({required: true})

const mailProviders = ['NONE', 'SMTP', 'RAPIDMAIL', 'TWILIO', 'SWEEGO', 'BREVO']
</script>

<template>
  <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
    <div>
      <FieldLabel class="mb-1">{{ t('adminSettings.mailing.provider') }}</FieldLabel>
      <SelectInput v-model="mailingConfig.provider" class="w-full">
        <option v-for="p in mailProviders" :key="p" :value="p">{{ p }}</option>
      </SelectInput>
    </div>
    <MailingTextField v-model="mailingConfig.senderAddress" :label="t('adminSettings.mailing.senderAddress')"/>
    <MailingTextField v-model="mailingConfig.senderName" :label="t('adminSettings.mailing.senderName')"/>
    <MailingTextField v-model="mailingConfig.user" :label="t('adminSettings.mailing.user')"/>
    <MailingTextField v-model="mailingConfig.password" :label="t('adminSettings.mailing.password')"/>
    <MailingTextField v-model="mailingConfig.apiKey" :label="t('adminSettings.mailing.apiKey')"/>
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
</template>
