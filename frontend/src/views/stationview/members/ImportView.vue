/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import UploadStep from './importview/UploadStep.vue'
import MappingStep from './importview/MappingStep.vue'
import type { ColumnMapping } from './importview/MappingStep.vue'
import PreviewStep from './importview/PreviewStep.vue'
import type { PreviewResult } from './importview/PreviewStep.vue'
import DoneStep from './importview/DoneStep.vue'
import type { ImportResult } from './importview/DoneStep.vue'
import client from '@/api/client'
import { profileFields as profileFieldsApi } from '@/api'
import type { ProfileField } from '@/api/types'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const router = useRouter()
const { loaded } = useSession()

type Step = 'upload' | 'mapping' | 'preview' | 'done'

const step = ref<Step>('upload')
const csvText = ref('')
const fileName = ref('')
const separator = ref(';')
const managerCount = ref(2)
const headers = ref<string[]>([])
const sampleRows = ref<string[][]>([])
const mappings = ref<ColumnMapping[]>([])
const fields = ref<ProfileField[]>([])
const preview = ref<PreviewResult | null>(null)
const result = ref<ImportResult | null>(null)
const loading = ref(false)
const error = ref('')

const targetOptions = computed(() => {
  const opts: { value: string; label: string; group?: string }[] = [
    { value: 'skip', label: t('memberImport.targetSkip') },
    { value: 'firstName', label: t('memberImport.targetFirstName'), group: t('memberImport.groupMember') },
    { value: 'lastName', label: t('memberImport.targetLastName'), group: t('memberImport.groupMember') },
    { value: 'email', label: t('memberImport.targetEmail'), group: t('memberImport.groupMember') },
    { value: 'group', label: t('memberImport.targetGroup'), group: t('memberImport.groupMember') },
  ]
  for (let i = 1; i <= managerCount.value; i++) {
    const g = t('memberImport.groupManager', { n: i })
    opts.push({ value: `manager:${i}:firstName`, label: t('memberImport.managerFirstName'), group: g })
    opts.push({ value: `manager:${i}:lastName`, label: t('memberImport.managerLastName'), group: g })
    opts.push({ value: `manager:${i}:phone`, label: t('memberImport.managerPhone'), group: g })
    opts.push({ value: `manager:${i}:email`, label: t('memberImport.managerEmail'), group: g })
  }
  const scopeLabels: Record<string, string> = {
    MEMBER: t('memberImport.scopeMember'),
    GUARDIAN: t('memberImport.scopeMemberManager'),
    TEAM: t('memberImport.scopeTeam'),
    GROUP: t('memberImport.scopeGroup'),
  }
  for (const f of fields.value) {
    const scopeLabel = scopeLabels[f.scope ?? 'MEMBER'] ?? f.scope
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
    groups.add(scopeLabels[f.scope ?? 'MEMBER'] ?? f.scope ?? '')
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
  for (let i = 1; i <= managerCount.value; i++) {
    if (h.includes(`kontakt ${i}`) || h.includes(`contact ${i}`)) return `manager:${i}:firstName`
    if (h === `email ${i}`) return `manager:${i}:email`
  }
  const phoneMatch = h.match(/(?:telefon|phone).*?(\d)/)
  if (phoneMatch) {
    const n = parseInt(phoneMatch[1])
    if (n >= 1 && n <= managerCount.value) return `manager:${n}:phone`
  }
  for (const f of fields.value) {
    if (f.name?.toLowerCase() === h) return `field:${f.id}`
  }
  return 'skip'
}

function needsValueMap(mapping: ColumnMapping): boolean {
  if (!mapping.target.startsWith('field:')) return false
  const fieldId = parseInt(mapping.target.substring(6))
  const field = fields.value.find(f => f.id === fieldId)
  return field?.fieldType === 'BOOLEAN' || field?.fieldType === 'ENUM'
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
      csvColumn: h, target: guessTarget(h), mergeOrder: idx, mergeSeparator: ' ', valueMap: {}, splitChar: '', splitIndex: 0,
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
    const res = await client.post<PreviewResult>('/members/import/preview', {
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
    const res = await client.post<ImportResult>('/members/import', {
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
  managerCount.value = 2
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
      <div class="flex items-center justify-between">
        <SectionHeader>{{ t('memberImport.title') }}</SectionHeader>
        <SecondaryButton :icon="['fas', 'chevron-left']" @click="router.push({ name: 'members-create' })">
          {{ t('common.back') }}
        </SecondaryButton>
      </div>

      <!-- Import type selector -->
      <div v-if="step === 'upload'" class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <NeutralContainer class="space-y-2 border-primary ring-2 ring-primary/30">
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'users']" class="text-primary" />
            <span class="font-semibold">{{ t('memberImport.modeMembers') }}</span>
          </div>
          <p class="text-sm text-(--text-muted)">{{ t('memberImport.modeMembersHint') }}</p>
        </NeutralContainer>
        <NeutralContainer clickable class="space-y-2" @click="router.push({ name: 'members-import-team' })">
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'user-shield']" class="text-primary" />
            <span class="font-semibold">{{ t('teamImport.modeTeam') }}</span>
          </div>
          <p class="text-sm text-(--text-muted)">{{ t('teamImport.modeTeamHint') }}</p>
        </NeutralContainer>
      </div>

      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <UploadStep
        v-if="step === 'upload'"
        :file-name="fileName"
        :csv-text="csvText"
        :separator="separator"
        :manager-count="managerCount"
        :loading="loading"
        @file-upload="handleFileUpload"
        @update:separator="separator = $event"
        @update:manager-count="managerCount = $event"
        @parse="parseCsv"
      />

      <MappingStep
        v-if="step === 'mapping'"
        :mappings="mappings"
        :headers="headers"
        :sample-rows="sampleRows"
        :target-options="targetOptions"
        :field-scope-groups="fieldScopeGroups"
        :manager-count="managerCount"
        :loading="loading"
        :needs-value-map-fn="needsValueMap"
        @update:mappings="mappings = $event"
        @back="step = 'upload'"
        @preview="loadPreview"
      />

      <Spinner v-if="loading" size="lg" />

      <PreviewStep
        v-if="step === 'preview' && preview"
        :preview="preview"
        :loading="loading"
        @back="step = 'mapping'"
        @import="doImport"
      />

      <DoneStep
        v-if="step === 'done' && result"
        :result="result"
        @start-over="startOver"
        @to-list="router.push({ name: 'members-list' })"
      />
    </div>
  </ViewContent>
</template>
