/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {listTrustedDevices, revokeAllTrustedDevices, revokeTrustedDevice} from '@/api/twoFactor'
import type {TrustedDevice} from '@/api/twoFactor'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'

const {t} = useI18n()

const devices = ref<TrustedDevice[]>([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    devices.value = await listTrustedDevices()
  } catch (e: any) {
    error.value = e?.response?.data?.message || t('common.error')
  }
  loading.value = false
}

async function handleRevoke(device: TrustedDevice) {
  try {
    await revokeTrustedDevice(device.id)
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message || t('common.error')
  }
}

async function handleRevokeAll() {
  if (devices.value.length === 0) return
  try {
    await revokeAllTrustedDevices()
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message || t('common.error')
  }
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('de-DE')
}

onMounted(load)
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
    <Spinner v-if="loading" size="sm"/>
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <MutedText v-if="!loading && devices.length === 0" tag="p" size="sm">
      {{ t('twoFactor.trustedDevices.empty') }}
    </MutedText>
    <ul v-if="!loading && devices.length > 0" class="space-y-2">
      <li v-for="device in devices" :key="device.id"
          class="flex items-center justify-between gap-2 rounded border border-(--border) px-3 py-2">
        <div class="min-w-0">
          <div class="text-sm font-medium truncate">{{ device.userAgent || t('twoFactor.trustedDevices.unknownDevice') }}</div>
          <MutedText tag="div" size="sm">
            {{ t('twoFactor.trustedDevices.lastSeen') }}: {{ formatDate(device.lastSeenAt) }}
            · {{ t('twoFactor.trustedDevices.expires') }}: {{ formatDate(device.trustedUntil) }}
          </MutedText>
        </div>
        <ErrorButton size="sm" @click="handleRevoke(device)">{{ t('common.remove') }}</ErrorButton>
      </li>
    </ul>
  </NeutralContainer>
</template>
