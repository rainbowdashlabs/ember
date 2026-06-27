/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import SetupLayout from '@/views/stationview/setup/SetupLayout.vue'
import {nextStep, stepRouteName} from '@/views/stationview/setup/steps'

const {t} = useI18n()
const router = useRouter()

function start() {
    const next = nextStep('welcome')
    if (next) router.push({name: stepRouteName(next)})
}
</script>

<template>
  <SetupLayout step-id="welcome" :save-label="t('setup.actions.getStarted')" @save="start">
    <p>{{ t('setup.steps.welcome.body1') }}</p>
    <p>{{ t('setup.steps.welcome.body2') }}</p>
    <ul class="list-disc list-inside text-sm text-(--text-muted) space-y-1">
      <li>{{ t('setup.steps.welcome.bullet1') }}</li>
      <li>{{ t('setup.steps.welcome.bullet2') }}</li>
      <li>{{ t('setup.steps.welcome.bullet3') }}</li>
    </ul>
  </SetupLayout>
</template>
