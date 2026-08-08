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
import {nextStepHref} from '@/views/stationview/setup/steps'

const {t} = useI18n()
const router = useRouter()

function goCreateEvent() {
    router.push({path: '/station/events/new', query: {returnTo: nextStepHref(router, 'first-event')}})
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
          :to="nextStepHref(router, 'first-event')"
          class="text-sm text-(--text-muted) underline"
      >
        {{ t('setup.actions.skip') }}
      </RouterLink>
    </div>
  </SetupLayout>
</template>
