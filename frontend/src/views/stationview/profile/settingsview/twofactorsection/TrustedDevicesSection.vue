/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {listTrustedDevices, revokeAllTrustedDevices, revokeTrustedDevice, type TrustedDevice} from '@/api/twoFactor'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {formatDateTime} from '@/util/format'

const {t} = useI18n()

function describeError(e: unknown): string {
  return (e as {response?: {data?: {message?: string}}})?.response?.data?.message || t('common.error')
}

const {config: devices, loading, error, runWith} = useConfigPanel<TrustedDevice[]>({
  initial: [],
  fetch: () => listTrustedDevices(),
  formatError: describeError,
})

async function handleRevoke(device: TrustedDevice) {
  await runWith(async () => {
    await revokeTrustedDevice(device.id)
    return await listTrustedDevices()
  })
}

async function handleRevokeAll() {
  if (devices.value.length === 0) return
  await runWith(async () => {
    await revokeAllTrustedDevices()
    return await listTrustedDevices()
  })
}

</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center justify-between gap-2">
      <SubHeader>{{ t('twoFactor.trustedDevices.title') }}</SubHeader>
      <SecondaryButton v-if="devices.length > 0" size="sm" @click="handleRevokeAll">
        {{ t('twoFactor.trustedDevices.revokeAll') }}
      </SecondaryButton>
    </div>
    <MutedText tag="p" size="sm">{{ t('twoFactor.trustedDevices.description') }}</MutedText>
    <AsyncSection
        :empty="devices.length === 0"
        :empty-compact="true"
        :empty-message="t('twoFactor.trustedDevices.empty')"
        :error="error"
        :loading="loading"
        spinner-size="sm"
    >
      <ul class="space-y-2">
        <li v-for="device in devices" :key="device.id"
            class="flex items-center justify-between gap-2 rounded border border-(--border) px-3 py-2">
          <div class="min-w-0">
            <div class="text-sm font-medium truncate">{{ device.userAgent || t('twoFactor.trustedDevices.unknownDevice') }}</div>
            <MutedText tag="div" size="sm">
              {{ t('twoFactor.trustedDevices.lastSeen') }}: {{ formatDateTime(device.lastSeenAt) }}
              · {{ t('twoFactor.trustedDevices.expires') }}: {{ formatDateTime(device.trustedUntil) }}
            </MutedText>
          </div>
          <ErrorButton compact @click="handleRevoke(device)">{{ t('common.remove') }}</ErrorButton>
        </li>
      </ul>
    </AsyncSection>
  </NeutralContainer>
</template>
