/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'

const emit = defineEmits<{
  (e: 'navigate'): void
}>()

const {t} = useI18n()
const {hasPermission} = useSession()
</script>

<template>
  <SidebarLink v-if="hasPermission(StationPermission.TEST_CATALOG_VIEW)" :icon="['fas', 'book']" name="quiz-catalogs" to="/station/quiz/catalogs" @navigate="emit('navigate')">
    {{ t('sidebar.quizCatalogs') }}
  </SidebarLink>
  <SidebarLink :icon="['fas', 'file-lines']" name="quiz-tests" to="/station/quiz/tests" @navigate="emit('navigate')">
    {{ t('sidebar.quizTests') }}
  </SidebarLink>
  <SidebarLink :icon="['fas', 'brain']" name="quiz-training" to="/station/quiz/training" @navigate="emit('navigate')">
    {{ t('sidebar.quizTraining') }}
  </SidebarLink>
</template>
