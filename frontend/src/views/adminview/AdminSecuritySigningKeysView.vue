/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Alert from '@/components/feedback/Alert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SigningKeyHealthPanel from './adminsecuritysigningkeysview/SigningKeyHealthPanel.vue'
import SigningKeyRecoveryHistory from './adminsecuritysigningkeysview/SigningKeyRecoveryHistory.vue'
import SigningKeyRecoveryModal from './adminsecuritysigningkeysview/SigningKeyRecoveryModal.vue'
import {signing} from '@/api'
import type {SigningKeyStatus} from '@/api/generated/schema'
import {useConfigPanel} from '@/composables/useConfigPanel'

/**
 * The signing keys of the installation for its administrator: whether they still open under the at-rest
 * secret, and giving up the ones that do not, so the stations can seal again.
 *
 * <p>Giving up sends exactly the serial numbers shown; the server refuses when the keys that do not open
 * have changed since, and asks for a fresh second factor first.
 */
const {t} = useI18n()

const {config: status, loading, failure, runWith} = useConfigPanel<SigningKeyStatus>({
  initial: {locked: [], openKeys: 0, recoveries: []},
  fetch: () => signing.getSigningKeyStatus(),
})

const confirming = ref(false)
const recovering = ref(false)
const recovered = ref(false)

async function recover() {
  const serialNumbers = status.value.locked.map(key => key.serialNumber)
  await runWith(async () => {
    await signing.recoverSigningKeys(serialNumbers)
    recovered.value = true
    return signing.getSigningKeyStatus()
  }, {busy: recovering})
  confirming.value = false
}
</script>

<template>
  <ViewContent :title="t('pages.admin-security-signing-keys.title')" :subtitle="t('pages.admin-security-signing-keys.subtitle')">
    <div class="space-y-6">
      <MutedText tag="p" size="sm">{{ t('adminSecurity.signingKeys.hint') }}</MutedText>
      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure"/>
      <Alert v-if="recovered" variant="success" data-testid="signing-keys-recovered">
        {{ t('adminSecurity.signingKeys.recovered') }}
      </Alert>

      <template v-if="!loading">
        <SigningKeyHealthPanel :status="status" @recover="confirming = true"/>
        <SigningKeyRecoveryHistory :recoveries="status.recoveries"/>
      </template>

      <SigningKeyRecoveryModal v-model="confirming" :count="status.locked.length" :busy="recovering" @confirm="recover"/>
    </div>
  </ViewContent>
</template>
