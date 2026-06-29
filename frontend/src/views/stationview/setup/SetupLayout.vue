/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useSetupStatus} from '@/composables/useSetupStatus'
import {
    nextStep,
    prevStep,
    STEP_ORDER,
    stepRouteName,
    type WizardStepId,
} from '@/views/stationview/setup/steps'

const props = defineProps<{
    stepId: WizardStepId
    skippable?: boolean
    saveLabel?: string
    saveDisabled?: boolean
    saving?: boolean
    hideActions?: boolean
}>()

const emit = defineEmits<{
    (e: 'save'): void
}>()

const {t} = useI18n()
const router = useRouter()
const {hasPermission, loaded: sessionLoaded, load: loadSession, sessionInfo} = useSession()
const {load: loadStatus} = useSetupStatus()

onMounted(async () => {
    if (!sessionLoaded.value) await loadSession()
    if (!hasPermission(StationPermission.STATION_ADMINISTRATOR)) {
        router.replace('/station/dashboard/overview')
        return
    }
    if (sessionInfo.value?.setupCompletedAt) {
        router.replace('/station/dashboard/overview')
        return
    }
    await loadStatus()
})

const prev = computed(() => prevStep(props.stepId))
const next = computed(() => nextStep(props.stepId))
const currentIndex = computed(() => STEP_ORDER.indexOf(props.stepId))
const totalSteps = computed(() => STEP_ORDER.length)
const progressPct = computed(() =>
    currentIndex.value < 0 ? 0 : Math.round(((currentIndex.value + 1) / totalSteps.value) * 100),
)

function goBack() {
    if (prev.value) router.push({name: stepRouteName(prev.value)})
}

function goSkip() {
    if (next.value) router.push({name: stepRouteName(next.value)})
}
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <header class="space-y-3">
        <div class="flex items-center gap-3 flex-wrap">
          <SectionHeader class="flex-1">{{ t(`setup.steps.${stepId}.title`) }}</SectionHeader>
          <span class="text-xs text-(--text-muted) whitespace-nowrap">
            {{ t('setup.stepCounter', {current: currentIndex + 1, total: totalSteps}) }}
          </span>
        </div>
        <div class="h-1 bg-(--bg-accent) rounded-full overflow-hidden">
          <div class="h-full bg-(--accent) transition-all" :style="{width: progressPct + '%'}"/>
        </div>
        <p class="text-sm text-(--text-muted)">{{ t(`setup.steps.${stepId}.hint`) }}</p>
      </header>
      <div class="space-y-4">
        <slot/>
      </div>
      <footer
          v-if="!hideActions"
          class="flex flex-wrap items-center gap-3 justify-between pt-4 border-t border-(--border)"
      >
        <SecondaryButton v-if="prev" :disabled="saving" @click="goBack">
          {{ t('setup.actions.back') }}
        </SecondaryButton>
        <span v-else/>
        <div class="flex items-center gap-3">
          <SecondaryButton v-if="skippable && next" :disabled="saving" @click="goSkip">
            {{ t('setup.actions.skip') }}
          </SecondaryButton>
          <PrimaryButton :disabled="saveDisabled || saving" @click="emit('save')">
            {{ saveLabel ?? t('setup.actions.saveAndContinue') }}
          </PrimaryButton>
        </div>
      </footer>
    </div>
  </ViewContent>
</template>
