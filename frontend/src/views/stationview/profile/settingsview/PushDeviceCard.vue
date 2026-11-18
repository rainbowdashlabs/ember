/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {listPushDevices, registerPushDevice, removePushDevice, testPushDevice} from '@/api/push'
import {getStatus as getMessagesStatus, updateSettings as updateMessageSettings} from '@/api/messages'
import type {MessagesStatus, PushDevice} from '@/api/generated/schema'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {currentBrowserSubscription, deviceLabel, pushState, subscribeBrowser, unsubscribeBrowser, type PushState} from '@/util/pushSupport'
import {describeFailure, type Failure} from '@/util/failure'
import {formatDateTime} from '@/util/format'

/**
 * Push messages on this device and the account's other devices.
 *
 * <p>Nothing is asked of the browser until the button is pressed: browsers refuse a permission prompt
 * that is not the answer to a click, and a prompt on arrival is the one people learn to dismiss. What
 * this browser can do decides what the card offers: a button, the way to add Ember to an iPhone's
 * home screen first, how to unblock a browser that was told no, or a plain note that it cannot.
 */
const props = defineProps<{
  /** The installation's key, which this browser subscribes with. */
  publicKey: string
}>()

const {t} = useI18n()

const state = ref<PushState>('unsupported')
const devices = ref<PushDevice[]>([])
const busy = ref(false)
const tested = ref<number | null>(null)
const declined = ref(false)
const failure = ref<Failure | null>(null)

const thisDevice = computed(() => devices.value.find(device => device.current) ?? null)

/** The reader's messages, whose pushes are one switch for the whole account; absent where messages are not theirs. */
const messages = ref<MessagesStatus | null>(null)

function toggleMessages(value: boolean) {
  return guarded(async () => {
    messages.value = await updateMessageSettings({push: value})
  })
}

async function guarded(action: () => Promise<void>) {
  busy.value = true
  failure.value = null
  try {
    await action()
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    busy.value = false
  }
}

async function loadDevices() {
  devices.value = await listPushDevices()
}

function enable() {
  return guarded(async () => {
    declined.value = false
    const subscription = await subscribeBrowser(props.publicKey)
    state.value = pushState()
    if (!subscription) {
      declined.value = true
      return
    }
    await registerPushDevice({...subscription, label: deviceLabel()})
    await loadDevices()
  })
}

function remove(device: PushDevice) {
  return guarded(async () => {
    if (device.current) await unsubscribeBrowser()
    await removePushDevice(device.id)
    await loadDevices()
  })
}

function test(device: PushDevice) {
  return guarded(async () => {
    await testPushDevice(device.id)
    tested.value = device.id
  })
}

/**
 * Registers this browser again without asking, where it still holds its subscription and the
 * permission but Ember no longer knows it: a subscription ends with its sign-in, and nobody should
 * have to switch push on again after every sign-in. A browser removed by hand stays removed; the
 * server leaves it out of a renewal.
 */
async function renewSilently() {
  if (state.value !== 'allowed' || thisDevice.value) return
  const subscription = await currentBrowserSubscription()
  if (!subscription) return
  await registerPushDevice({...subscription, label: deviceLabel(), renewal: true})
  await loadDevices()
}

onMounted(() => {
  state.value = pushState()
  void guarded(async () => {
    await loadDevices()
    await renewSilently()
  })
  getMessagesStatus()
      .then(status => { messages.value = status.available ? status : null })
      .catch(() => { messages.value = null })
})
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="push-device-card" data-onboarding="push.card">
    <SubHeader class="text-sm">{{ t('pushDevices.title') }}</SubHeader>
    <p class="text-xs text-(--text-muted)">{{ t('pushDevices.hint') }}</p>

    <FailureAlert :failure="failure"/>

    <Alert v-if="state === 'installFirst'" variant="info" data-testid="push-install-first">
      {{ t('pushDevices.installFirst') }}
    </Alert>
    <Alert v-else-if="state === 'unsupported'" variant="info" data-testid="push-unsupported">
      {{ t('pushDevices.unsupported') }}
    </Alert>
    <Alert v-else-if="state === 'blocked'" variant="info" data-testid="push-blocked">
      {{ t('pushDevices.blocked') }}
    </Alert>
    <template v-else-if="!thisDevice">
      <Alert v-if="declined" variant="info">{{ t('pushDevices.declined') }}</Alert>
      <ButtonRow>
        <PrimaryButton :icon="['fas', 'bell']" :disabled="busy" data-testid="push-enable"
                       data-onboarding="push.enable" @click="enable">
          {{ t('pushDevices.enable') }}
        </PrimaryButton>
      </ButtonRow>
    </template>

    <ul v-if="devices.length" class="space-y-2" data-testid="push-devices">
      <li v-for="device in devices" :key="device.id"
          class="flex flex-wrap items-center justify-between gap-2 border-t border-(--border) pt-2"
          data-testid="push-device">
        <div class="min-w-0">
          <span class="text-sm font-medium">{{ device.label || t('pushDevices.unnamed') }}</span>
          <span v-if="device.current" class="ml-2 text-xs text-primary">{{ t('pushDevices.thisDevice') }}</span>
          <p class="text-xs text-(--text-muted)">
            {{ device.lastSuccessAt
              ? t('pushDevices.lastReached', {when: formatDateTime(device.lastSuccessAt)})
              : t('pushDevices.notReachedYet') }}
          </p>
          <p v-if="tested === device.id" class="text-xs text-success">{{ t('pushDevices.testSent') }}</p>
        </div>
        <ButtonRow>
          <SecondaryButton :disabled="busy" data-testid="push-test" @click="test(device)">
            {{ t('pushDevices.test') }}
          </SecondaryButton>
          <SecondaryButton :disabled="busy" data-testid="push-remove" @click="remove(device)">
            {{ t('pushDevices.remove') }}
          </SecondaryButton>
        </ButtonRow>
      </li>
    </ul>

    <div v-if="messages" class="flex items-center justify-between gap-4 border-t border-(--border) pt-2">
      <div>
        <p class="text-sm font-medium">{{ t('pushDevices.messages') }}</p>
        <p class="text-xs text-(--text-muted)">{{ t('pushDevices.messagesHint') }}</p>
      </div>
      <ToggleInput :model-value="messages.push" :aria-label="t('pushDevices.messages')" :disabled="busy"
                   data-testid="push-messages-toggle" @update:model-value="toggleMessages"/>
    </div>

    <p class="text-xs text-(--text-muted)">{{ t('pushDevices.lockScreen') }}</p>
    <p class="text-xs text-(--text-muted)">{{ t('pushDevices.signIn') }}</p>
  </NeutralContainer>
</template>
