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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {useSetupStatus} from '@/composables/useSetupStatus'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {goToNextStep} from '@/views/stationview/setup/steps'
import type {Failure} from '@/util/failure'

const {t} = useI18n()
const router = useRouter()
const {reload, requiredSteps} = useSetupStatus()

/**
 * The three things this step can have to say, kept apart because they are not alike.
 *
 * <p>An address that saved, an address that would not save, and an address that is only half filled
 * in used to share one line and one alert. The last of those is the reader's to finish and never a
 * fault in Ember, so it must not come with the offer to report a bug; the middle one often is, and
 * has to keep that offer along with whatever the server said about why.
 */
const message = ref('')
const failure = ref<Failure | null>(null)
const incomplete = ref('')

function onSuccess(text: string) {
  message.value = text
  failure.value = null
  incomplete.value = ''
}

function onError(reported: Failure) {
  failure.value = reported
  message.value = ''
  incomplete.value = ''
}

const {running: saving, run: proceed} = useAsyncAction(async () => {
  await reload()
  const addressStep = requiredSteps.value.find((s) => s.id === 'address')
  if (!addressStep?.complete) {
    incomplete.value = t('setup.steps.address.incompleteHint')
    message.value = ''
    failure.value = null
    return
  }
  goToNextStep(router, 'address')
})
</script>

<template>
  <SetupLayout step-id="address" :saving="saving" @save="proceed">
    <LocationSection @success="onSuccess" @error="onError"/>
    <Alert v-if="message" variant="success">{{ message }}</Alert>
    <FailureAlert :message="incomplete" expected/>
    <FailureAlert :failure="failure"/>
  </SetupLayout>
</template>
