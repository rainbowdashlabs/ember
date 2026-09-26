/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PasskeysInsights from '@/views/adminview/adminsecuritytwofactorview/PasskeysInsights.vue'
import {adminSettings} from '@/api'
import {PasskeyMode, type PasskeysConfig, type PasskeyModeName} from '@/api/adminSettings'
import {apiErrorMessage} from '@/util/apiError'
import {describeFailure, type Failure} from '@/util/failure'
import {useConfigPanel} from '@/composables/useConfigPanel'

const {t} = useI18n()

const MODES = Object.values(PasskeyMode)

const {config, loading, failure, reload, runWith} = useConfigPanel<PasskeysConfig>({
  initial: {
    mode: 'OPTIONAL',
    effectiveMode: 'OPTIONAL',
    localhostFallback: false,
    rpId: '',
    lastMailSentAt: null,
    dependentAccounts: 0,
    accountsWithTriedPasskey: 0,
    accountsWithPassword: 0,
    accountsWithPasswordAndNoPasskey: 0,
  },
  fetch: () => adminSettings.getPasskeysConfig(),
})

const saveFailure = ref<Failure | null>(null)

const heldAtOff = computed(() => config.value.effectiveMode === 'OFF' && config.value.mode !== 'OFF')

async function save() {
  saveFailure.value = null
  try {
    await runWith(() => adminSettings.updatePasskeysConfig(config.value.mode as PasskeyModeName), {rethrow: true})
  } catch (e) {
    saveFailure.value = {
      ...describeFailure(e, t),
      message: apiErrorMessage(e) ?? t('adminSecurity.passkeys.saveFailed'),
    }
    await reload()
    throw e
  }
}
</script>

<template>
  <div>
    <Spinner v-if="loading" size="md"/>
    <FailureAlert :failure="failure"/>

    <NeutralContainer v-if="!loading" class="space-y-4">
      <SectionHeader>{{ t('adminSecurity.passkeys.title') }}</SectionHeader>
      <MutedText tag="p" size="sm">{{ t('adminSecurity.passkeys.hint') }}</MutedText>

      <div>
        <FieldLabel class="mb-1">{{ t('adminSecurity.passkeys.mode') }}</FieldLabel>
        <SelectInput v-model="config.mode" class="w-full">
          <option v-for="m in MODES" :key="m" :value="m">
            {{ t(`adminSecurity.passkeys.modes.${m}`) }}
          </option>
        </SelectInput>
        <MutedText tag="div" class="mt-1" size="sm">
          {{ t(`adminSecurity.passkeys.modeHints.${config.mode}`) }}
        </MutedText>
      </div>

      <Alert v-if="heldAtOff" variant="error">
        {{ t('adminSecurity.passkeys.heldAtOff', {rpId: config.rpId}) }}
      </Alert>

      <PasskeysInsights :config="config"/>

      <FailureAlert :failure="saveFailure"/>
      <div class="flex justify-end">
        <SaveButton :action="save"/>
      </div>
    </NeutralContainer>
  </div>
</template>
