/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import { adminSettings } from '@/api'
import type { TotpConfig } from '@/api/adminSettings'

const { t } = useI18n()

const TOTP_ALGORITHMS = ['SHA1', 'SHA256', 'SHA512'] as const

const loading = ref(true)
const error = ref('')
const config = ref<TotpConfig>({
  digits: 6,
  periodSeconds: 30,
  algorithm: 'SHA1',
  driftWindow: 1,
  issuer: 'Ember',
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    config.value = await adminSettings.getTotpConfig()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function save() {
  error.value = ''
  try {
    config.value = await adminSettings.updateTotpConfig(config.value)
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

onMounted(load)
</script>

<template>
  <div>
    <Spinner v-if="loading" size="md"/>
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <NeutralContainer v-if="!loading" class="space-y-4">
      <SectionHeader>{{ t('adminSecurity.totp.title') }}</SectionHeader>
      <MutedText tag="p" size="sm">{{ t('adminSecurity.totp.hint') }}</MutedText>

      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <FieldLabel class="mb-1">{{ t('adminSecurity.totp.digits') }}</FieldLabel>
          <NumberInput v-model="config.digits"/>
          <MutedText tag="div" class="mt-1" size="sm">{{ t('adminSecurity.totp.digitsHint') }}</MutedText>
        </div>
        <div>
          <FieldLabel class="mb-1">{{ t('adminSecurity.totp.periodSeconds') }}</FieldLabel>
          <NumberInput v-model="config.periodSeconds"/>
          <MutedText tag="div" class="mt-1" size="sm">{{ t('adminSecurity.totp.periodSecondsHint') }}</MutedText>
        </div>
        <div>
          <FieldLabel class="mb-1">{{ t('adminSecurity.totp.algorithm') }}</FieldLabel>
          <SelectInput v-model="config.algorithm" class="w-full">
            <option v-for="a in TOTP_ALGORITHMS" :key="a" :value="a">{{ a }}</option>
          </SelectInput>
          <MutedText tag="div" class="mt-1" size="sm">{{ t('adminSecurity.totp.algorithmHint') }}</MutedText>
        </div>
        <div>
          <FieldLabel class="mb-1">{{ t('adminSecurity.totp.driftWindow') }}</FieldLabel>
          <NumberInput v-model="config.driftWindow"/>
          <MutedText tag="div" class="mt-1" size="sm">{{ t('adminSecurity.totp.driftWindowHint') }}</MutedText>
        </div>
        <div class="md:col-span-2">
          <FieldLabel class="mb-1">{{ t('adminSecurity.totp.issuer') }}</FieldLabel>
          <TextInput v-model="config.issuer"/>
          <MutedText tag="div" class="mt-1" size="sm">{{ t('adminSecurity.totp.issuerHint') }}</MutedText>
        </div>
      </div>

      <div class="flex justify-end">
        <SaveButton :action="save"/>
      </div>
    </NeutralContainer>
  </div>
</template>
