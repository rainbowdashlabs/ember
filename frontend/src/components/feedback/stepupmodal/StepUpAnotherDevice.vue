/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onBeforeUnmount, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import {passkeys} from '@/api'

/**
 * Confirming a sensitive action on a device the reader is already signed in on.
 *
 * <p>The answer for somebody with no password and no passkey on the machine in front of them, which
 * on a passwordless instance is every borrowed machine. This half shows a code and waits; the other
 * device types it into its settings, sees what it is confirming, and answers for itself.
 */
const props = defineProps<{
  /** What the step-up was demanded for, and the only thing a confirmation answers. */
  category: string
}>()

const emit = defineEmits<{
  confirmed: []
}>()

const {t} = useI18n()

type Phase = 'idle' | 'starting' | 'waiting' | 'expired' | 'failed'
const phase = ref<Phase>('idle')
const code = ref('')
let pollTimer: ReturnType<typeof setInterval> | null = null

/** Read in fours, because eight characters in a row are read back wrongly. */
const grouped = computed(() => (code.value.length === 8 ? `${code.value.slice(0, 4)}-${code.value.slice(4)}` : code.value))

function stopPolling() {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = null
}

async function start() {
  stopPolling()
  phase.value = 'starting'
  try {
    const request = await passkeys.stepUpDeviceBegin(props.category)
    code.value = request.code
    phase.value = 'waiting'
    pollTimer = setInterval(() => void poll(request.pollSecret), 2500)
  } catch {
    phase.value = 'failed'
  }
}

/** One tick of the wait. A lost poll is nothing: the next tick asks again. */
async function poll(pollSecret: string) {
  if (phase.value !== 'waiting') return
  try {
    const result = await passkeys.stepUpDevicePoll(pollSecret)
    if (result.status === 'CONFIRMED') {
      stopPolling()
      emit('confirmed')
    } else if (result.status === 'EXPIRED' || result.status === 'UNKNOWN') {
      stopPolling()
      phase.value = 'expired'
    }
  } catch {
    return
  }
}

onBeforeUnmount(stopPolling)
</script>

<template>
  <div class="space-y-3">
    <PrimaryButton v-if="phase === 'idle'" :icon="['fas', 'mobile-screen']" @click="start">
      {{ t('twoFactor.stepUp.anotherDevice.start') }}
    </PrimaryButton>

    <Spinner v-else-if="phase === 'starting'" size="sm"/>

    <template v-else-if="phase === 'waiting'">
      <MutedText tag="p" size="sm">{{ t('twoFactor.stepUp.anotherDevice.hint') }}</MutedText>
      <p class="text-center font-mono text-2xl tracking-widest" data-testid="stepup-device-code">{{ grouped }}</p>
      <div class="flex items-center justify-center gap-2">
        <Spinner size="sm"/>
        <MutedText size="sm">{{ t('twoFactor.stepUp.anotherDevice.waiting') }}</MutedText>
      </div>
    </template>

    <template v-else>
      <Alert variant="error">
        {{ phase === 'expired' ? t('twoFactor.stepUp.anotherDevice.expired') : t('common.error') }}
      </Alert>
      <SecondaryButton @click="start">{{ t('twoFactor.stepUp.anotherDevice.again') }}</SecondaryButton>
    </template>
  </div>
</template>
