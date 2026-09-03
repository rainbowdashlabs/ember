/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import {passkeys} from '@/api'
import type {DeviceLookup} from '@/api/passkeys'
import {apiErrorStatus} from '@/util/apiError'
import {formatDateTime} from '@/util/format'

/**
 * The approving half of the device handshake: type the code the new device shows, see what is
 * about to be approved in large plain words, and approve only when you are sitting at that
 * device yourself. The approval itself stands behind the step-up proof.
 */
const {t} = useI18n()

const code = ref('')
const details = ref<DeviceLookup | null>(null)
const error = ref('')
const done = ref(false)
const busy = ref(false)

async function lookup() {
  error.value = ''
  busy.value = true
  try {
    details.value = await passkeys.deviceLookup(code.value)
  } catch (e) {
    details.value = null
    error.value = apiErrorStatus(e) === 404 ? t('passkeys.approve.unknownCode') : t('common.error')
  } finally {
    busy.value = false
  }
}

async function approve() {
  error.value = ''
  busy.value = true
  try {
    await passkeys.deviceApprove(code.value)
    done.value = true
  } catch (e) {
    error.value = apiErrorStatus(e) === 404 ? t('passkeys.approve.unknownCode') : t('common.error')
  } finally {
    busy.value = false
  }
}

function reset() {
  code.value = ''
  details.value = null
  done.value = false
  error.value = ''
}
</script>

<template>
  <ViewContent :title="t('pages.account-unlock-device.title')" :subtitle="t('pages.account-unlock-device.subtitle')">
    <div class="max-w-xl mx-auto space-y-6 p-4">
      <NeutralContainer class="space-y-4">
        <SectionHeader>{{ t('passkeys.approve.title') }}</SectionHeader>
        <MutedText tag="p" size="sm">{{ t('passkeys.approve.hint') }}</MutedText>
        <Alert v-if="error" variant="error">{{ error }}</Alert>

        <template v-if="done">
          <Alert variant="success">{{ t('passkeys.approve.done') }}</Alert>
          <SecondaryButton type="button" @click="reset">{{ t('passkeys.approve.another') }}</SecondaryButton>
        </template>

        <template v-else-if="!details">
          <TextInput v-model="code" placeholder="K7RM-2WQD" class="font-mono tracking-widest"/>
          <PrimaryButton type="button" :disabled="busy || code.replace(/[\s-]/g, '').length < 8" @click="lookup">
            {{ t('passkeys.approve.lookup') }}
          </PrimaryButton>
        </template>

        <template v-else>
          <div class="space-y-1 text-sm">
            <div class="font-medium text-base">{{ details.userAgent || t('passkeys.approve.unknownDevice') }}</div>
            <div>{{ t('passkeys.approve.place', {place: details.country || '?'}) }}</div>
            <div>{{ t('passkeys.approve.when', {when: formatDateTime(details.createdAt)}) }}</div>
          </div>
          <Alert variant="error">{{ t('passkeys.approve.warning') }}</Alert>
          <div class="flex justify-between gap-2">
            <SecondaryButton type="button" :disabled="busy" @click="reset">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton type="button" :disabled="busy" @click="approve">
              {{ t('passkeys.approve.approve') }}
            </PrimaryButton>
          </div>
        </template>
      </NeutralContainer>
    </div>
  </ViewContent>
</template>
