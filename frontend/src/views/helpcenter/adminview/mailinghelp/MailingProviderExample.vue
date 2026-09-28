/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {RELAY_PROVIDER_NAMES} from '@/util/mailProviders'

/**
 * A mail provider as the administration page shows it, filled in for an own mail server, so the
 * help page can show the fields without anybody having to open the real form.
 */
const {t} = useI18n()

const textFields = [
  {label: 'mailChain.senderAddress', value: 'noreply@beispiel.de'},
  {label: 'mailChain.senderName', value: 'Jugendfeuerwehr Musterstadt'},
  {label: 'mailChain.host', value: 'mail.beispiel.de'},
  {label: 'mailChain.user', value: 'noreply@beispiel.de'},
  {label: 'mailChain.password', value: '••••••••'},
]

const numberFields = [
  {label: 'mailChain.port', value: 587, hint: ''},
  {label: 'mailChain.attempts', value: 2, hint: 'mailChain.attemptsHint'},
  {label: 'mailChain.dailyLimit', value: 300, hint: 'mailChain.dailyLimitHint'},
]
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('mailChain.title') }}</SectionHeader>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <div>
        <FieldLabel class="mb-1">{{ t('mailChain.provider') }}</FieldLabel>
        <SelectInput :model-value="'SMTP'" class="w-full">
          <option value="SMTP">{{ t('mailChain.ownServer') }}</option>
          <option v-for="(name, key) in RELAY_PROVIDER_NAMES" :key="key" :value="key">{{ name }}</option>
        </SelectInput>
      </div>
      <div v-for="field in textFields" :key="field.label">
        <FieldLabel class="mb-1">{{ t(field.label) }}</FieldLabel>
        <TextInput :model-value="field.value"/>
      </div>
      <div v-for="field in numberFields" :key="field.label">
        <FieldLabel class="mb-1">{{ t(field.label) }}</FieldLabel>
        <NumberInput :model-value="field.value"/>
        <MutedText v-if="field.hint" tag="div" class="mt-1">{{ t(field.hint) }}</MutedText>
      </div>
    </div>

    <div class="flex justify-end">
      <PrimaryButton>{{ t('adminSettings.legal.save') }}</PrimaryButton>
    </div>
  </NeutralContainer>
</template>
