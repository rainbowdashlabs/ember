/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import InfoContainer from '@/components/container/InfoContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useSetupStatus} from '@/composables/useSetupStatus'
import {stepRouteName, type WizardStepId} from '@/views/stationview/setup/steps'

const {t} = useI18n()
const router = useRouter()
const {hasPermission, sessionInfo} = useSession()
const {load, completedAt, requiredSteps, optionalSteps, hasIncompleteApplicable} = useSetupStatus()

const isAdministrator = computed(() => hasPermission(StationPermission.STATION_ADMINISTRATOR))

const visible = computed(() => {
    if (!isAdministrator.value) return false
    if (completedAt.value) return false
    if (sessionInfo.value?.setupCompletedAt) return false
    return hasIncompleteApplicable.value
})

onMounted(async () => {
    if (isAdministrator.value) await load()
})

function backendToFrontend(id: string): WizardStepId {
    switch (id) {
        case 'memberTypes': return 'member-types'
        case 'firstEvent': return 'first-event'
        case 'kbSeed': return 'kb-seed'
        default: return id as WizardStepId
    }
}

function goStep(id: string) {
    router.push({name: stepRouteName(backendToFrontend(id))})
}

function resumeWizard() {
    router.push({name: 'station-setup'})
}
</script>

<template>
  <InfoContainer v-if="visible" class="space-y-3">
    <div class="flex items-center gap-3 flex-wrap">
      <SectionHeader>{{ t('setup.checklist.title') }}</SectionHeader>
      <span class="text-xs text-(--text-muted) flex-1">{{ t('setup.checklist.hint') }}</span>
      <PrimaryButton @click="resumeWizard">{{ t('setup.checklist.resume') }}</PrimaryButton>
    </div>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-1">
      <DropdownMenuItem
          v-for="step in requiredSteps"
          :key="step.id"
          @click="goStep(step.id)"
      >
        <span :class="step.complete ? 'text-(--success)' : 'text-(--text-muted)'">
          {{ step.complete ? '✓' : '○' }}
        </span>
        <span class="flex-1">{{ t(`setup.steps.${backendToFrontend(step.id)}.label`) }}</span>
        <span class="text-xs text-(--text-muted)">{{ t('setup.checklist.required') }}</span>
      </DropdownMenuItem>
      <DropdownMenuItem
          v-for="step in optionalSteps"
          :key="step.id"
          :disabled="!step.applicable"
          @click="goStep(step.id)"
      >
        <span :class="step.complete ? 'text-(--success)' : 'text-(--text-muted)'">
          {{ step.complete ? '✓' : '○' }}
        </span>
        <span class="flex-1">{{ t(`setup.steps.${backendToFrontend(step.id)}.label`) }}</span>
        <span class="text-xs text-(--text-muted)">{{ t('setup.checklist.optional') }}</span>
      </DropdownMenuItem>
    </div>
  </InfoContainer>
</template>
