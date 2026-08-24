/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import SidebarExpandableLink from '@/components/navigation/SidebarExpandableLink.vue'

defineProps<{
  close: () => void
}>()

const {t} = useI18n()
</script>

<template>
  <SidebarGroup :icon="['fas', 'graduation-cap']" :label="t('sidebar.quiz')"
                :prefix="['/helpcenter/station/quiz', '/helpcenter/station/protocols']"
                to="/helpcenter/station/quiz" name="help-quiz-module-overview" @navigate="close">
    <SidebarExpandableLink :icon="['fas', 'book']" name="help-quiz-catalogs"
                           to="/helpcenter/station/quiz/catalogs" prefix="/helpcenter/station/quiz/catalog" @navigate="close">
      <template #label>{{ t('sidebar.quizCatalogs') }}</template>
      <SidebarLink :icon="['fas', 'eye']" name="help-quiz-catalog-detail" to="/helpcenter/station/quiz/catalogs/0" @navigate="close">
        {{ t('sidebar.quizCatalogDetail') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'robot']" name="help-quiz-ai" to="/helpcenter/station/quiz/ai" @navigate="close">
        {{ t('sidebar.quizAi') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'robot']" name="help-quiz-catalog-generate" to="/helpcenter/station/quiz/catalogs/0/generate" @navigate="close">
        {{ t('sidebar.quizCatalogGenerate') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'file-import']" name="help-quiz-catalog-import" to="/helpcenter/station/quiz/catalogs/0/import" @navigate="close">
        {{ t('sidebar.quizCatalogImport') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'star']" name="help-quiz-catalog-mc-fill" to="/helpcenter/station/quiz/catalogs/0/mc-fill" @navigate="close">
        {{ t('helpCenter.quizCatalogMcFill.title') }}
      </SidebarLink>
    </SidebarExpandableLink>
    <SidebarExpandableLink :icon="['fas', 'file-lines']" name="help-quiz-tests"
                           to="/helpcenter/station/quiz/tests" prefix="/helpcenter/station/quiz/tests" @navigate="close">
      <template #label>{{ t('sidebar.quizTests') }}</template>
      <SidebarLink :icon="['fas', 'plus']" name="help-quiz-test-create" to="/helpcenter/station/quiz/tests/create" @navigate="close">
        {{ t('sidebar.quizTestCreate') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'eye']" name="help-quiz-test-detail" to="/helpcenter/station/quiz/tests/0" @navigate="close">
        {{ t('sidebar.quizTestDetail') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'pen']" name="help-quiz-test-edit" to="/helpcenter/station/quiz/tests/0/edit" @navigate="close">
        {{ t('sidebar.quizTestEdit') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'pen']" name="help-quiz-test-take" to="/helpcenter/station/quiz/tests/0/take" @navigate="close">
        {{ t('sidebar.quizTestTake') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'star']" name="help-quiz-test-evaluate" to="/helpcenter/station/quiz/tests/0/evaluate/0" @navigate="close">
        {{ t('sidebar.quizTestEvaluate') }}
      </SidebarLink>
    </SidebarExpandableLink>
    <SidebarLink :icon="['fas', 'brain']" name="help-quiz-training" to="/helpcenter/station/quiz/training" @navigate="close">
      {{ t('sidebar.quizTraining') }}
    </SidebarLink>
    <SidebarExpandableLink :icon="['fas', 'clipboard-check']" name="help-protocol-module-overview" to="/helpcenter/station/protocols" prefix="/helpcenter/station/protocols" @navigate="close">
      <template #label>{{ t('sidebar.protocols') }}</template>
      <SidebarLink :icon="['fas', 'eye']" name="help-protocol-detail" to="/helpcenter/station/protocols/0" @navigate="close">
        {{ t('sidebar.protocolDetail') }}
      </SidebarLink>
      <SidebarExpandableLink :icon="['fas', 'list-check']" name="help-protocol-run-list" to="/helpcenter/station/protocols/runs" prefix="/helpcenter/station/protocols/runs" @navigate="close">
        <template #label>{{ t('sidebar.protocolRuns') }}</template>
        <SidebarLink :icon="['fas', 'eye']" name="help-protocol-run-detail" to="/helpcenter/station/protocols/runs/0" @navigate="close">
          {{ t('sidebar.protocolRunDetail') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'chart-bar']" name="help-protocol-evaluation" to="/helpcenter/station/protocols/runs/0/evaluation" @navigate="close">
          {{ t('sidebar.protocolEvaluation') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'pen-to-square']" name="help-protocol-grade" to="/helpcenter/station/protocols/runs/0/grade/0" @navigate="close">
          {{ t('sidebar.protocolGrade') }}
        </SidebarLink>
      </SidebarExpandableLink>
    </SidebarExpandableLink>
  </SidebarGroup>
</template>
