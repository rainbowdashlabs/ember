/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import client from '@/api/client'
import { profileFields as profileFieldsApi } from '@/api'
import type { ProfileField } from '@/api/types'
import { useSession } from '@/composables/useSession'
import TeamImportHeader from './teamimportview/TeamImportHeader.vue'
import UploadStep from './teamimportview/UploadStep.vue'
import MappingStep from './teamimportview/MappingStep.vue'
import type { ColumnMapping } from './teamimportview/MappingStep.vue'
import PreviewStep from './teamimportview/PreviewStep.vue'
import type { PreviewResult } from './teamimportview/PreviewStep.vue'
import DoneStep from './teamimportview/DoneStep.vue'
import type { TeamImportResult } from './teamimportview/DoneStep.vue'

const { t } = useI18n()
const { loaded } = useSession()

type Step = 'upload' | 'mapping' | 'preview' | 'done'

const step = ref<Step>('upload')
const csvText = ref('')
const fileName = ref('')
const separator = ref(';')
const headers = ref<string[]>([])
const sampleRows = ref<string[][]>([])
const mappings = ref<ColumnMapping[]>([])
const fields = ref<ProfileField[]>([])
const preview = ref<PreviewResult | null>(null)
const result = ref<TeamImportResult | null>(null)
const loading = ref(false)
const error = ref('')

const targetOptions = computed(() => {
  const opts: { value: string; label: string; group?: string }[] = [
    { value: 'skip', label: t('memberImport.targetSkip') },
    { value: 'firstName', label: t('memberImport.targetFirstName'), group: t('teamImport.groupTeam') },
    { value: 'lastName', label: t('memberImport.targetLastName'), group: t('teamImport.groupTeam') },
    { value: 'email', label: t('memberImport.targetEmail'), group: t('teamImport.groupTeam') },
    { value: 'group', label: t('memberImport.targetGroup'), group: t('teamImport.groupTeam') },
  ]
  const scopeLabels: Record<string, string> = {
    MEMBER: t('memberImport.scopeMember'),
    GUARDIAN: t('memberImport.scopeMemberManager'),
    TEAM: t('memberImport.scopeTeam'),
    GROUP: t('memberImport.scopeGroup'),
  }
  for (const f of fields.value) {
    const scopeLabel = scopeLabels[f.scope ?? 'TEAM'] ?? f.scope
    opts.push({ value: `field:${f.id}`, label: `${f.name} (${f.fieldType})`, group: scopeLabel })
  }
  return opts
})

const fieldScopeGroups = computed(() => {
  const scopeLabels: Record<string, string> = {
    MEMBER: t('memberImport.scopeMember'),
    GUARDIAN: t('memberImport.scopeMemberManager'),
    TEAM: t('memberImport.scopeTeam'),
    GROUP: t('memberImport.scopeGroup'),
  }
  const groups = new Set<string>()
  for (const f of fields.value) {
    groups.add(scopeLabels[f.scope ?? 'TEAM'] ?? f.scope ?? '')
  }
  return [...groups]
})

const MAX_CSV_SIZE = 2 * 1024 * 1024

async function handleFileUpload(file: File) {
  if (file.size > MAX_CSV_SIZE) {
    error.value = t('memberImport.fileTooLarge')
    return
  }
  error.value = ''
  fileName.value = file.name
  csvText.value = await file.text()
}

function guessTarget(header: string): string {
  const h = header.toLowerCase().trim()
  if (h === 'vorname' || h === 'first name' || h === 'firstname') return 'firstName'
  if (h === 'name' || h === 'nachname' || h === 'last name' || h === 'lastname') return 'lastName'
  if (h === 'email' || h === 'e-mail') return 'email'
  if (h === 'gruppe' || h === 'group') return 'group'
  for (const f of fields.value) {
    if (f.name?.toLowerCase() === h) return `field:${f.id}`
  }
  return 'skip'
}

async function parseCsv() {
  loading.value = true
  error.value = ''
  try {
    const [parseRes, fieldsRes] = await Promise.all([
      client.post<{ headers: string[]; rows: string[][] }>('/members/import/parse', { csv: csvText.value, separator: separator.value }),
      profileFieldsApi.listFields(),
    ])
    headers.value = parseRes.data.headers
    sampleRows.value = parseRes.data.rows.slice(0, 3)
    fields.value = fieldsRes
    mappings.value = headers.value.map((h, idx) => ({
      csvColumn: h, target: guessTarget(h), mergeOrder: idx, mergeSeparator: ' ',
      valueMap: {}, splitChar: '', splitIndex: 0,
    }))
    step.value = 'mapping'
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function loadPreview() {
  loading.value = true
  error.value = ''
  try {
    const res = await client.post<PreviewResult>('/members/import-team/preview', {
      csv: csvText.value, separator: separator.value, mappings: mappings.value,
    })
    preview.value = res.data
    step.value = 'preview'
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function doImport() {
  loading.value = true
  error.value = ''
  try {
    const res = await client.post<TeamImportResult>('/members/import-team', {
      csv: csvText.value, separator: separator.value, mappings: mappings.value,
    })
    result.value = res.data
    step.value = 'done'
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function startOver() {
  step.value = 'upload'
  csvText.value = ''
  fileName.value = ''
  headers.value = []
  mappings.value = []
  preview.value = null
  result.value = null
}

onMounted(async () => {
  if (loaded.value) {
    fields.value = await profileFieldsApi.listFields()
  }
})

watch(loaded, async (isLoaded) => {
  if (isLoaded) fields.value = await profileFieldsApi.listFields()
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <TeamImportHeader />
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <UploadStep
        v-if="step === 'upload'"
        :file-name="fileName" :csv-text="csvText" :separator="separator" :loading="loading"
        @file-upload="handleFileUpload"
        @update:separator="separator = $event"
        @parse="parseCsv"
      />
      <MappingStep
        v-if="step === 'mapping'"
        v-model:mappings="mappings"
        :headers="headers" :sample-rows="sampleRows"
        :target-options="targetOptions" :field-scope-groups="fieldScopeGroups" :loading="loading"
        @back="step = 'upload'"
        @preview="loadPreview"
      />
      <Spinner v-if="loading" size="lg" />
      <PreviewStep
        v-if="step === 'preview' && preview"
        :preview="preview" :loading="loading"
        @back="step = 'mapping'" @import="doImport"
      />
      <DoneStep
        v-if="step === 'done' && result"
        :result="result" @start-over="startOver"
      />
    </div>
  </ViewContent>
</template>
