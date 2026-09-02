/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import CsvImportWizard from '@/components/csv/CsvImportWizard.vue'
import ImportHeader from './importview/ImportHeader.vue'
import ModeSelector from './importview/ModeSelector.vue'
import ManagerCountSelect from './importview/ManagerCountSelect.vue'
import MappingStep from './importshared/MappingStep.vue'
import PreviewStep from './importshared/PreviewStep.vue'
import SetupMailChoice from '@/components/input/toggle/SetupMailChoice.vue'
import { useMemberCsvImport } from './importshared/useMemberCsvImport'
import DoneStep from './importview/DoneStep.vue'
import type { ImportResult } from './importview/DoneStep.vue'
import { CsvImportSteps } from '@/composables/useCsvImport'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()

const managerCount = ref(2)

const { importer, targetOptions, fieldScopeGroups, needsValueMap, valuesForTarget, toggleRow, fieldLabel, sendSetupMail } = useMemberCsvImport<ImportResult>({
  previewPath: '/members/import/preview',
  importPath: '/members/import',
  defaultScope: 'MEMBER',
  primaryGroup: () => t('memberImport.groupMember'),
  managerCount,
})

const { step, mapping, headers, rows, preview, result } = importer

function leaveImport() {
  const returnTo = typeof route.query.returnTo === 'string' ? route.query.returnTo : null
  if (returnTo && returnTo.startsWith('/')) router.push(returnTo)
  else router.push({ name: 'members-list' })
}
</script>

<template>
  <ViewContent
      :title="t('pages.members-import.title')"
      :subtitle="t('pages.members-import.subtitle')"
  >
    <div class="space-y-6">
      <ImportHeader />
      <ModeSelector v-if="step === CsvImportSteps.UPLOAD" />

      <CsvImportWizard :importer="importer" accept=".csv,.txt">
        <template #uploadOptions>
          <ManagerCountSelect v-model="managerCount" />
        </template>

        <template #mapping>
          <MappingStep
              v-model:mappings="mapping"
              :headers="headers"
              :rows="rows"
              :target-options="targetOptions"
              :field-scope-groups="fieldScopeGroups"
              :primary-group-label="t('memberImport.groupMember')"
              :manager-count="managerCount"
              :needs-value-map-fn="needsValueMap"
              :values-for-target="valuesForTarget"
          />
        </template>

        <template #preview>
          <PreviewStep
              v-if="preview"
              :preview="preview"
              :title="t('memberImport.previewTitle', { count: preview.members.length })"
              show-contacts
          :field-label="fieldLabel"
              @toggle-row="toggleRow"
              />
          <SetupMailChoice v-model="sendSetupMail" class="mt-4"/>
        </template>

        <template #done>
          <DoneStep
              v-if="result"
              :result="result"
              @start-over="importer.reset()"
              @to-list="leaveImport()"
          />
        </template>
      </CsvImportWizard>
    </div>
  </ViewContent>
</template>
