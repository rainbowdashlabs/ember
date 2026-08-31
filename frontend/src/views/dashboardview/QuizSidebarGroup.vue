/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarSubGroup from '@/components/navigation/SidebarSubGroup.vue'
import QuizSidebarLinks from '@/views/dashboardview/quizsidebargroup/QuizSidebarLinks.vue'
import ProtocolSidebarLinks from '@/views/dashboardview/quizsidebargroup/ProtocolSidebarLinks.vue'
import {StationModules, StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'

defineProps<{
  openGroup: string | null
  isDesktop: boolean
}>()

const emit = defineEmits<{
  (e: 'update:openGroup', value: string | null): void
  (e: 'navigate'): void
}>()

const {t} = useI18n()
const {isModuleEnabled, hasPermission, canTestProtocol} = useSession()

const quiz = computed(() => isModuleEnabled(StationModules.QUIZ))

/**
 * Whether tests are part of this group at all.
 *
 * <p>Not only whether the station runs them, but whether this reader has anything to do with them.
 * Somebody who may neither write a test sheet nor sit one was shown a heading called "Prüfungen"
 * with nothing under it, and the group was named after a half of it they could not open. For them
 * the whole thing now reads exactly as it does at a station that does not run tests.
 */
const protocols = computed(() => isModuleEnabled(StationModules.TEST_PROTOCOL)
    && (hasPermission(StationPermission.PROTOCOL_CREATE) || canTestProtocol()))

/**
 * One group carries two features, so it is named after the ones it still carries: a station that
 * runs its tests but no quiz should not read "Quiz" in the sidebar.
 */
const label = computed(() => {
  if (quiz.value && protocols.value) return t('sidebar.quizAndProtocols')
  if (protocols.value) return t('sidebar.protocolsGroup')
  return t('sidebar.quiz')
})

/**
 * With both features present the group would be a flat list of five, so each gets a section of its
 * own. With only one there is nothing to separate, and a section would be a level for its own sake.
 */
const sectioned = computed(() => quiz.value && protocols.value)

function close() {
  emit('navigate')
}
</script>

<template>
  <SidebarGroup v-if="quiz || protocols" data-onboarding="nav.quiz" :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => emit('update:openGroup', v)" :icon="['fas', 'graduation-cap']" :label="label" :prefix="['/station/quiz', '/station/protocols']" group-key="quiz-protocols">
    <template v-if="sectioned">
      <SidebarSubGroup :icon="['fas', 'graduation-cap']" :label="t('sidebar.quiz')" prefix="/station/quiz">
        <QuizSidebarLinks @navigate="close"/>
      </SidebarSubGroup>
      <SidebarSubGroup :icon="['fas', 'clipboard-list']" :label="t('sidebar.protocolsGroup')" prefix="/station/protocols">
        <ProtocolSidebarLinks @navigate="close"/>
      </SidebarSubGroup>
    </template>
    <template v-else>
      <QuizSidebarLinks v-if="quiz" @navigate="close"/>
      <ProtocolSidebarLinks v-if="protocols" @navigate="close"/>
    </template>
  </SidebarGroup>
</template>
