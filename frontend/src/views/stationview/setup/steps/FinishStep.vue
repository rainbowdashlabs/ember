/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SetupLayout from '@/views/stationview/setup/SetupLayout.vue'
import {useSession} from '@/composables/useSession'
import {useSetupStatus} from '@/composables/useSetupStatus'
import {useOnboardingTour} from '@/composables/useOnboardingTour'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {stepRouteName, type WizardStepId} from '@/views/stationview/setup/steps'

const {t} = useI18n()
const router = useRouter()
const {load: loadSession} = useSession()
const {load, markComplete, completedAt, optionalSteps} = useSetupStatus()
const {startTour} = useOnboardingTour()

const error = ref('')

onMounted(async () => {
    await load(true)
})

const {running: finishing, run: runFinalize} = useAsyncAction(async () => {
    error.value = ''
    const result = await markComplete()
    if (!result.ok) {
        error.value = t('setup.steps.finish.missingHint')
        const first = result.missingSteps[0]
        const map: Record<string, WizardStepId> = {
            address: 'address',
            modules: 'modules',
            memberTypes: 'member-types',
        }
        const fallback = (first ? map[first] : undefined) ?? 'welcome'
        router.replace({name: stepRouteName(fallback)})
        return false
    }
    await loadSession()
    return true
})

async function finalizeSetup(): Promise<boolean> {
    if (completedAt.value) return true
    return (await runFinalize()) ?? false
}

async function goDashboard() {
    const ok = await finalizeSetup()
    if (!ok) return
    await router.push('/station/dashboard/overview')
}

async function takeTour() {
    const ok = await finalizeSetup()
    if (!ok) return
    await router.push('/station/dashboard/overview')
    setTimeout(() => startTour(), 200)
}

const mailIncomplete = computed(
    () => optionalSteps.value.find((s) => s.id === 'mail' && !s.complete) != null,
)
</script>

<template>
  <SetupLayout step-id="finish" hide-actions @save="goDashboard">
    <div class="space-y-6 text-center">
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Spinner v-if="finishing" size="lg"/>
      <template v-else>
        <p>{{ t('setup.steps.finish.body') }}</p>
        <Alert v-if="mailIncomplete" variant="info">{{ t('setup.steps.finish.mailReminder') }}</Alert>
        <div class="flex flex-wrap items-center gap-3 justify-center pt-4">
          <PrimaryButton @click="takeTour">{{ t('setup.steps.finish.takeTour') }}</PrimaryButton>
          <SecondaryButton @click="goDashboard">{{ t('setup.steps.finish.goDashboard') }}</SecondaryButton>
        </div>
      </template>
    </div>
  </SetupLayout>
</template>
