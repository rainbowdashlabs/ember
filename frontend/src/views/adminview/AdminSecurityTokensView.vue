/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import TokenConfigPanel from '@/views/adminview/adminsecuritytokensview/TokenConfigPanel.vue'
import PepperPanel from '@/views/adminview/adminsecuritytokensview/PepperPanel.vue'
import {adminSettings} from '@/api'
import type {TokensConfigResponse} from '@/api/adminSettings'
import {useConfigPanel} from '@/composables/useConfigPanel'

const generating = ref(false)
const {config, loading, error, runWith} = useConfigPanel<TokensConfigResponse>({
  initial: {
    tokenBytes: 32,
    verifyTokenHours: 24,
    passwordTokenHours: 72,
    sessionMinutes: 30,
    tokenPepperConfigured: false,
  },
  fetch: () => adminSettings.getTokensConfig(),
})

async function save() {
  await runWith(
    () => adminSettings.updateTokensConfig({
      tokenBytes: config.value.tokenBytes,
      verifyTokenHours: config.value.verifyTokenHours,
      passwordTokenHours: config.value.passwordTokenHours,
      sessionMinutes: config.value.sessionMinutes,
    }),
    {rethrow: true},
  )
}

async function generatePepper() {
  await runWith(() => adminSettings.generateTokenPepper(), {busy: generating})
}
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <TokenConfigPanel :config="config" :save="save"/>
        <PepperPanel :configured="config.tokenPepperConfigured" :generating="generating" @generate="generatePepper"/>
      </template>
    </div>
  </ViewContent>
</template>
