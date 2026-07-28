/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import {StationPermission} from '@/api/types'
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
const {hasPermission, canTestProtocol} = useSession()

function close() {
  emit('navigate')
}
</script>

<template>
  <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => emit('update:openGroup', v)" :icon="['fas', 'graduation-cap']" :label="t('sidebar.quiz')" prefix="/station/quiz" group-key="quiz-protocols">
    <SidebarLink v-if="hasPermission(StationPermission.TEST_CATALOG_VIEW)" :icon="['fas', 'book']" name="quiz-catalogs" to="/station/quiz/catalogs" @navigate="close">
      {{ t('sidebar.quizCatalogs') }}
    </SidebarLink>
    <SidebarLink :icon="['fas', 'file-lines']" name="quiz-tests" to="/station/quiz/tests" @navigate="close">
      {{ t('sidebar.quizTests') }}
    </SidebarLink>
    <SidebarLink :icon="['fas', 'brain']" name="quiz-training" to="/station/quiz/training" @navigate="close">
      {{ t('sidebar.quizTraining') }}
    </SidebarLink>
    <SidebarLink v-if="hasPermission(StationPermission.PROTOCOL_CREATE)" :icon="['fas', 'clipboard-list']" name="protocol-list" to="/station/protocols" @navigate="close">
      {{ t('sidebar.protocols') }}
    </SidebarLink>
    <SidebarLink v-if="canTestProtocol()" :icon="['fas', 'clipboard-check']" name="protocol-run-list" to="/station/protocols/runs" @navigate="close">
      {{ t('sidebar.protocolRuns') }}
    </SidebarLink>
  </SidebarGroup>
</template>
