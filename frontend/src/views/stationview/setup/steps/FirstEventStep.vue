/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import SetupLayout from '@/views/stationview/setup/SetupLayout.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {nextStep, stepRouteName} from '@/views/stationview/setup/steps'

const {t} = useI18n()
const router = useRouter()

function goCreateEvent() {
    const next = nextStep('first-event')
    const returnTo = next ? router.resolve({name: stepRouteName(next)}).href : '/station/setup'
    router.push({path: '/station/events/new', query: {returnTo}})
}
</script>

<template>
  <SetupLayout
      step-id="first-event"
      skippable
      :save-label="t('setup.steps.first-event.openEditor')"
      hide-actions
      @save="goCreateEvent"
  >
    <InfoContainer class="space-y-2">
      <p class="font-medium text-sm">{{ t('setup.steps.first-event.aboutTitle') }}</p>
      <p class="text-sm">{{ t('setup.steps.first-event.aboutBody') }}</p>
    </InfoContainer>
    <div class="flex flex-wrap items-center gap-3">
      <PrimaryButton @click="goCreateEvent">
        {{ t('setup.steps.first-event.openEditor') }}
      </PrimaryButton>
      <RouterLink
          :to="{name: stepRouteName(nextStep('first-event')!)}"
          class="text-sm text-(--text-muted) underline"
      >
        {{ t('setup.actions.skip') }}
      </RouterLink>
    </div>
  </SetupLayout>
</template>
