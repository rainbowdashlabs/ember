/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, watch} from 'vue'
import NavSidebar from '@/components/navigation/NavSidebar.vue'
import {StationPermission} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'
import {useSetupStatus} from '@/composables/useSetupStatus'
import {useStationNav} from '@/composables/useStationNav'
import type {SetupNavStep} from '@/data/sidebar/stationNav'
import {STEP_ORDER, stepBackendId, stepRouteName} from '@/views/stationview/setup/steps'

const emit = defineEmits<{
  (e: 'navigate'): void
}>()

const {hasPermission, sessionInfo} = useSession()
const {load: loadSetup, requiredSteps, optionalSteps} = useSetupStatus()

/** The first setup's steps in wizard order, each with how far the station has come with it. */
const setupSteps = computed<SetupNavStep[]>(() => STEP_ORDER.map(stepId => {
  const state = [...requiredSteps.value, ...optionalSteps.value].find(step => step.id === stepBackendId(stepId))
  return {
    key: stepRouteName(stepId),
    to: `/station/setup/${stepId}`,
    label: `setup.steps.${stepId}.label`,
    complete: state?.complete ?? false,
    applicable: state?.applicable ?? true,
  }
}))

const {nodes} = useStationNav(setupSteps)

onMounted(() => watch(
    () => hasPermission(StationPermission.STATION_ADMINISTRATOR) && sessionInfo.value?.setupCompletedAt == null,
    setupPending => {
      if (setupPending) loadSetup()
    },
    {immediate: true}))
</script>

<template>
  <NavSidebar :nodes="nodes" @navigate="emit('navigate')"/>
</template>
