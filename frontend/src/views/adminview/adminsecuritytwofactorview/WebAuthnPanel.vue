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
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import { adminSettings } from '@/api'
import type { WebAuthnConfig } from '@/api/adminSettings'

const { t } = useI18n()

const ATTESTATIONS = ['none', 'indirect', 'direct'] as const

const loading = ref(true)
const error = ref('')
const config = ref<WebAuthnConfig>({
  rpId: '',
  rpName: '',
  attestation: 'none',
  timeoutSeconds: 60,
  requireResidentKey: false,
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    config.value = await adminSettings.getWebAuthnConfig()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function save() {
  error.value = ''
  try {
    config.value = await adminSettings.updateWebAuthnConfig(config.value)
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
      <SectionHeader>{{ t('adminSecurity.webauthn.title') }}</SectionHeader>
      <MutedText tag="p" size="sm">{{ t('adminSecurity.webauthn.hint') }}</MutedText>

      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <FieldLabel class="mb-1">{{ t('adminSecurity.webauthn.rpId') }}</FieldLabel>
          <TextInput v-model="config.rpId"/>
          <MutedText tag="div" class="mt-1" size="sm">{{ t('adminSecurity.webauthn.rpIdHint') }}</MutedText>
        </div>
        <div>
          <FieldLabel class="mb-1">{{ t('adminSecurity.webauthn.rpName') }}</FieldLabel>
          <TextInput v-model="config.rpName"/>
          <MutedText tag="div" class="mt-1" size="sm">{{ t('adminSecurity.webauthn.rpNameHint') }}</MutedText>
        </div>
        <div>
          <FieldLabel class="mb-1">{{ t('adminSecurity.webauthn.attestation') }}</FieldLabel>
          <SelectInput v-model="config.attestation" class="w-full">
            <option v-for="a in ATTESTATIONS" :key="a" :value="a">
              {{ t(`adminSecurity.webauthn.attestationOptions.${a}`) }}
            </option>
          </SelectInput>
          <MutedText tag="div" class="mt-1" size="sm">{{ t('adminSecurity.webauthn.attestationHint') }}</MutedText>
        </div>
        <div>
          <FieldLabel class="mb-1">{{ t('adminSecurity.webauthn.timeoutSeconds') }}</FieldLabel>
          <NumberInput v-model="config.timeoutSeconds"/>
          <MutedText tag="div" class="mt-1" size="sm">{{ t('adminSecurity.webauthn.timeoutSecondsHint') }}</MutedText>
        </div>
      </div>

      <div class="flex items-center justify-between">
        <div>
          <div class="font-medium">{{ t('adminSecurity.webauthn.requireResidentKey') }}</div>
          <MutedText tag="div" size="sm">{{ t('adminSecurity.webauthn.requireResidentKeyHint') }}</MutedText>
        </div>
        <ToggleInput v-model="config.requireResidentKey"/>
      </div>

      <div class="flex justify-end">
        <SaveButton :action="save"/>
      </div>
    </NeutralContainer>
  </div>
</template>
