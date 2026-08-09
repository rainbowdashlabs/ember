/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import SetupLayout from '@/views/stationview/setup/SetupLayout.vue'
import LocationSection from '@/views/stationview/manage/stationview/LocationSection.vue'
import Alert from '@/components/feedback/Alert.vue'
import {useSetupStatus} from '@/composables/useSetupStatus'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {goToNextStep} from '@/views/stationview/setup/steps'

const {t} = useI18n()
const router = useRouter()
const {reload, requiredSteps} = useSetupStatus()

const message = ref('')
const messageVariant = ref<'success' | 'error'>('success')

function onSuccess(text: string) {
  message.value = text
  messageVariant.value = 'success'
}

function onError(text: string) {
  message.value = text
  messageVariant.value = 'error'
}

const {running: saving, run: proceed} = useAsyncAction(async () => {
  await reload()
  const addressStep = requiredSteps.value.find((s) => s.id === 'address')
  if (!addressStep?.complete) {
    onError(t('setup.steps.address.incompleteHint'))
    return
  }
  goToNextStep(router, 'address')
})
</script>

<template>
  <SetupLayout step-id="address" :saving="saving" @save="proceed">
    <LocationSection @success="onSuccess" @error="onError"/>
    <Alert v-if="message" :variant="messageVariant">{{ message }}</Alert>
  </SetupLayout>
</template>
