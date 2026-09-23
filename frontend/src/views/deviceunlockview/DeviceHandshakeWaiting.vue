/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import type {DeviceRequest} from '@/api/passkeys'

/**
 * What the asking device shows while it waits.
 *
 * <p>The QR carries the code, so the other device only has to scan it. The number does not travel
 * with it and is shown large here instead: whoever is holding the phone is asked for it, and a
 * picture of this screen forwarded to somebody else does not bring the number along. That is the
 * whole reason the code may sit in the QR at all.
 */
const {t} = useI18n()

const props = defineProps<{
  request: DeviceRequest
  wantsSignIn: boolean
  /** Set while the server has asked this device to slow down, so the wait can say why. */
  throttled?: boolean
}>()

/** Split in the middle, because eight unbroken characters are read back wrong. */
const groupedCode = computed(() => {
  const code = props.request.code
  return code.length === 8 ? `${code.slice(0, 4)}-${code.slice(4)}` : code
})
</script>

<template>
  <div class="space-y-4">
    <p>{{ wantsSignIn ? t('passkeys.device.instructionSignIn') : t('passkeys.device.instruction') }}</p>

    <img
        :alt="t('passkeys.device.qrAlt')"
        class="mx-auto h-48 w-48 rounded bg-white p-2"
        :src="`data:image/png;base64,${request.qrPng}`"
    />

    <div class="rounded-theme border border-(--border) p-4">
      <MutedText tag="p" size="sm">{{ t('passkeys.device.numberIntro') }}</MutedText>
      <div class="text-5xl font-bold tracking-widest" data-testid="device-match-number">
        {{ request.matchNumber }}
      </div>
    </div>

    <div>
      <MutedText tag="p" size="sm">{{ t('passkeys.device.codeFallback') }}</MutedText>
      <div class="font-mono text-2xl tracking-widest" data-testid="device-code">{{ groupedCode }}</div>
    </div>

    <MutedText tag="p" size="sm">
      {{ throttled ? t('passkeys.device.waitingThrottled') : t('passkeys.device.waiting') }}
    </MutedText>
  </div>
</template>
