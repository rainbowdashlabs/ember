/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import {useSetupStatus} from '@/composables/useSetupStatus'
import {STEP_ORDER, stepRouteName, type WizardStepId} from '@/views/stationview/setup/steps'

defineProps<{
  openGroup: string | null
  isDesktop: boolean
}>()

const emit = defineEmits<{
  (e: 'update:openGroup', value: string | null): void
  (e: 'navigate'): void
}>()

const {t} = useI18n()
const {load, requiredSteps, optionalSteps} = useSetupStatus()

onMounted(() => load())

function close() {
  emit('navigate')
}

function backendIdFor(stepId: WizardStepId): string {
    switch (stepId) {
        case 'member-types':
            return 'memberTypes'
        case 'first-event':
            return 'firstEvent'
        case 'kb-seed':
            return 'kbSeed'
        default:
            return stepId
    }
}

function findStep(stepId: WizardStepId) {
    const id = backendIdFor(stepId)
    return [...requiredSteps.value, ...optionalSteps.value].find((s) => s.id === id)
}

function isStepComplete(stepId: WizardStepId): boolean {
    return findStep(stepId)?.complete ?? false
}

function isStepApplicable(stepId: WizardStepId): boolean {
    const s = findStep(stepId)
    return s ? s.applicable : true
}

function stepPath(stepId: WizardStepId): string {
    return `/station/setup/${stepId}`
}
</script>

<template>
  <SidebarGroup
      :open-group="isDesktop ? undefined : openGroup"
      :icon="['fas', 'rocket']"
      :label="t('setup.headerTitle')"
      prefix="/station/setup"
      to="/station/setup"
      name="station-setup"
      @update:open-group="(v) => emit('update:openGroup', v)"
      @navigate="close"
  >
    <SidebarLink
        v-for="stepId in STEP_ORDER"
        :key="stepId"
        :icon="['fas', isStepComplete(stepId as WizardStepId) ? 'circle-check' : 'circle']"
        :name="stepRouteName(stepId as WizardStepId)"
        :to="stepPath(stepId as WizardStepId)"
        :class="!isStepApplicable(stepId as WizardStepId) ? 'opacity-40' : ''"
        @navigate="close"
    >
      {{ t(`setup.steps.${stepId}.label`) }}
    </SidebarLink>
  </SidebarGroup>
</template>
