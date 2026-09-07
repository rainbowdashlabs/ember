/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import CsvImportWizard from '@/components/csv/CsvImportWizard.vue'
import TeamImportHeader from './teamimportview/TeamImportHeader.vue'
import MappingStep from './importshared/MappingStep.vue'
import PreviewStep from './importshared/PreviewStep.vue'
import SetupMailChoice from '@/components/input/toggle/SetupMailChoice.vue'
import { useMemberCsvImport } from './importshared/useMemberCsvImport'
import DoneStep from './teamimportview/DoneStep.vue'
import type { TeamImportResult } from './teamimportview/DoneStep.vue'

const { t } = useI18n()

const { importer, targetOptions, fieldScopeGroups, needsValueMap, valuesForTarget, toggleRow, fieldLabel, sendSetupMail } = useMemberCsvImport<TeamImportResult>({
  previewPath: '/members/import-team/preview',
  importPath: '/members/import-team',
  defaultScope: 'TEAM',
  primaryGroup: () => t('teamImport.groupTeam'),
})

const { mapping, headers, rows, preview, result } = importer
</script>

<template>
  <ViewContent
      :title="t('pages.members-import-team.title')"
      :subtitle="t('pages.members-import-team.subtitle')"
  >
    <div class="space-y-6">
      <TeamImportHeader />

      <CsvImportWizard :importer="importer" accept=".csv,.txt">
        <template #mapping>
          <MappingStep
              v-model:mappings="mapping"
              :headers="headers"
              :rows="rows"
              :target-options="targetOptions"
              :field-scope-groups="fieldScopeGroups"
              :primary-group-label="t('teamImport.groupTeam')"
              :manager-count="0"
              :needs-value-map-fn="needsValueMap"
              :values-for-target="valuesForTarget"
          />
        </template>

        <template #preview>
          <PreviewStep
              v-if="preview"
              :preview="preview"
              :title="t('teamImport.previewTitle', { count: preview.members.length })"
          :field-label="fieldLabel"
              @toggle-row="toggleRow"
          />
          <SetupMailChoice v-model="sendSetupMail" class="mt-4"/>
        </template>

        <template #done>
          <DoneStep v-if="result" :result="result" @start-over="importer.reset()" />
        </template>
      </CsvImportWizard>
    </div>
  </ViewContent>
</template>
