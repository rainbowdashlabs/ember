/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import MailingTextField from './MailingTextField.vue'
import MailingNumberField from './MailingNumberField.vue'
import type {MailingConfig} from '@/api/adminSettings'

const {t} = useI18n()

const mailingConfig = defineModel<MailingConfig>({required: true})
</script>

<template>
  <div class="border-t border-(--border) pt-4 mt-4">
    <SubHeader>{{ t('adminSettings.mailing.smtp.title') }}</SubHeader>
    <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mt-3">
      <MailingTextField v-model="mailingConfig.smtpHost" :label="t('adminSettings.mailing.smtp.host')"/>
      <MailingNumberField v-model="mailingConfig.smtpPort" :label="t('adminSettings.mailing.smtp.port')"/>
      <div class="flex items-center gap-2 pt-6">
        <ToggleInput v-model="mailingConfig.smtpSsl"/>
        <FieldLabel>{{ t('adminSettings.mailing.smtp.ssl') }}</FieldLabel>
      </div>
    </div>
  </div>
</template>
