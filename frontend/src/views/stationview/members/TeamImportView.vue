/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import client from '@/api/client'
import { profileFields as profileFieldsApi } from '@/api'
import type { ProfileField } from '@/api/types'
import { useRouter } from 'vue-router'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const router = useRouter()
const { loaded } = useSession()

interface ColumnMapping { csvColumn: string; target: string; mergeOrder: number; mergeSeparator: string; valueMap: Record<string, string>; splitChar: string; splitIndex: number }
interface MemberPreview {
  firstName: string; lastName: string; email: string; group: string
  profileFields: Record<string, string>; contacts: unknown[]
}
interface PreviewResult { members: MemberPreview[]; warnings: string[] }
interface TeamImportResult { membersCreated: number; groupsAssigned: number; profileFieldsSet: number; warnings: string[] }

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
    MEMBER_MANAGER: t('memberImport.scopeMemberManager'),
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
    MEMBER_MANAGER: t('memberImport.scopeMemberManager'),
    TEAM: t('memberImport.scopeTeam'),
    GROUP: t('memberImport.scopeGroup'),
  }
  const groups = new Set<string>()
  for (const f of fields.value) {
    groups.add(scopeLabels[f.scope ?? 'TEAM'] ?? f.scope ?? '')
  }
  return [...groups]
})

const MAX_CSV_SIZE = 2 * 1024 * 1024 // 2 MB

async function handleFileUpload(file: File) {
  if (file.size > MAX_CSV_SIZE) {
    error.value = t('memberImport.fileTooLarge')
    return
  }
  error.value = ''
  fileName.value = file.name
  csvText.value = await file.text()
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
      csvColumn: h,
      target: guessTarget(h),
      mergeOrder: idx,
      mergeSeparator: ' ',
      valueMap: {},
      splitChar: '',
      splitIndex: 0,
    }))

    step.value = 'mapping'
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
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

function getSampleValues(colIndex: number): string[] {
  return sampleRows.value.map(row => row[colIndex] ?? '').filter(v => v)
}

function isMerged(target: string): boolean {
  if (target === 'skip') return false
  return mappings.value.filter(m => m.target === target).length > 1
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
      <div class="flex items-center justify-between">
        <SectionHeader>{{ t('teamImport.title') }}</SectionHeader>
        <SecondaryButton @click="router.push({ name: 'members-import' })">
          <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-2" />
          {{ t('common.back') }}
        </SecondaryButton>
      </div>

      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <!-- Step 1: Upload -->
      <template v-if="step === 'upload'">
        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('memberImport.upload') }}</SubHeader>
          <div class="flex items-center gap-4 flex-wrap">
            <FileUploadButton accept=".csv,.txt" @select="handleFileUpload">
              {{ t('memberImport.chooseFile') }}
            </FileUploadButton>
            <span v-if="fileName" class="text-sm">
              <font-awesome-icon :icon="['fas', 'check']" class="text-success mr-1" />
              {{ fileName }} ({{ csvText.split('\n').length - 1 }} {{ t('memberImport.rows') }})
            </span>
          </div>
          <div class="flex items-center gap-2">
            <label class="text-sm font-medium">{{ t('memberImport.separator') }}</label>
            <SelectInput v-model="separator" class="w-20">
              <option value=";">;</option>
              <option value=",">,</option>
              <option value="&#9;">Tab</option>
            </SelectInput>
          </div>
          <PrimaryButton :disabled="!csvText || loading" @click="parseCsv">
            {{ loading ? t('common.loading') : t('memberImport.next') }}
          </PrimaryButton>
        </NeutralContainer>
      </template>

      <!-- Step 2: Column Mapping -->
      <template v-if="step === 'mapping'">
        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('memberImport.mappingTitle') }}</SubHeader>
          <p class="text-sm text-(--text-muted)">{{ t('memberImport.mappingHint') }}</p>

          <div class="space-y-2">
            <div v-for="(m, i) in mappings" :key="i"
                 class="rounded-lg px-3 py-2"
                 :class="m.target === 'skip' ? 'opacity-50' : 'bg-bg-light-accent/20 dark:bg-bg-dark-accent/20'">
              <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 items-center">
                <div>
                  <span class="font-medium text-sm">{{ m.csvColumn }}</span>
                  <div class="text-xs text-(--text-muted) truncate">
                    {{ getSampleValues(headers.indexOf(m.csvColumn)).join(', ') || '—' }}
                  </div>
                </div>
                <div class="sm:col-span-2">
                  <SelectInput :model-value="m.target" class="flex-1" @update:model-value="mappings[i] = { ...m, target: $event as string }">
                    <option value="skip">{{ t('memberImport.targetSkip') }}</option>
                    <optgroup :label="t('teamImport.groupTeam')">
                      <option v-for="opt in targetOptions.filter(o => o.group === t('teamImport.groupTeam'))" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                    </optgroup>
                    <optgroup v-for="sg in fieldScopeGroups" :key="sg" :label="sg">
                      <option v-for="opt in targetOptions.filter(o => o.group === sg)" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                    </optgroup>
                  </SelectInput>
                </div>
              </div>
              <div v-if="m.target !== 'skip' && isMerged(m.target)" class="flex items-center gap-3 mt-2 text-xs">
                <span class="text-(--text-muted)">
                  <font-awesome-icon :icon="['fas', 'link']" class="mr-1" />
                  {{ t('memberImport.mergedWith') }}
                </span>
                <div class="flex items-center gap-1">
                  <label class="text-(--text-muted)">{{ t('memberImport.order') }}:</label>
                  <input type="number" :value="m.mergeOrder" class="w-12 px-1 py-0.5 rounded border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark text-center text-xs"
                    @input="mappings[i] = { ...m, mergeOrder: Number(($event.target as HTMLInputElement).value) }" />
                </div>
                <div class="flex items-center gap-1">
                  <label class="text-(--text-muted)">{{ t('memberImport.sep') }}:</label>
                  <input type="text" :value="m.mergeSeparator" class="w-10 px-1 py-0.5 rounded border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark text-center text-xs"
                    @input="mappings[i] = { ...m, mergeSeparator: ($event.target as HTMLInputElement).value }" />
                </div>
              </div>
            </div>
          </div>
        </NeutralContainer>

        <div class="flex justify-between">
          <SecondaryButton @click="step = 'upload'">{{ t('common.back') }}</SecondaryButton>
          <PrimaryButton :disabled="loading" @click="loadPreview">
            <font-awesome-icon :icon="['fas', 'eye']" class="mr-1" />
            {{ loading ? t('common.loading') : t('memberImport.preview') }}
          </PrimaryButton>
        </div>
      </template>

      <Spinner v-if="loading" size="lg" />

      <!-- Step 3: Preview -->
      <template v-if="step === 'preview' && preview">
        <Alert v-for="w in preview.warnings" :key="w" variant="info">{{ w }}</Alert>

        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('teamImport.previewTitle', { count: preview.members.length }) }}</SubHeader>
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent">
                  <th class="text-left py-2 px-2">{{ t('memberImport.name') }}</th>
                  <th class="text-left py-2 px-2">{{ t('memberImport.email') }}</th>
                  <th class="text-left py-2 px-2">{{ t('memberImport.group') }}</th>
                  <th class="text-left py-2 px-2">{{ t('memberImport.fields') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(m, i) in preview.members" :key="i" class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
                  <td class="py-2 px-2 font-medium">{{ m.firstName }} {{ m.lastName }}</td>
                  <td class="py-2 px-2 text-xs">{{ m.email }}</td>
                  <td class="py-2 px-2">{{ m.group }}</td>
                  <td class="py-2 px-2 text-xs">
                    <span v-for="(v, k) in m.profileFields" :key="k" class="inline-block mr-2">
                      <span class="text-(--text-muted)">{{ k }}:</span> {{ v }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </NeutralContainer>

        <div class="flex justify-between">
          <SecondaryButton @click="step = 'mapping'">{{ t('common.back') }}</SecondaryButton>
          <PrimaryButton :disabled="loading" @click="doImport">
            <font-awesome-icon :icon="['fas', 'download']" class="mr-1" />
            {{ loading ? t('common.loading') : t('memberImport.import') }}
          </PrimaryButton>
        </div>
      </template>

      <!-- Step 4: Done -->
      <template v-if="step === 'done' && result">
        <SuccessContainer class="space-y-3">
          <SubHeader>{{ t('memberImport.done') }}</SubHeader>
          <div class="grid grid-cols-3 gap-3 text-center">
            <div><p class="text-xl font-bold">{{ result.membersCreated }}</p><p class="text-xs text-(--text-muted)">{{ t('teamImport.membersCreated') }}</p></div>
            <div><p class="text-xl font-bold">{{ result.groupsAssigned }}</p><p class="text-xs text-(--text-muted)">{{ t('memberImport.groupsAssigned') }}</p></div>
            <div><p class="text-xl font-bold">{{ result.profileFieldsSet }}</p><p class="text-xs text-(--text-muted)">{{ t('memberImport.fieldsSet') }}</p></div>
          </div>
          <Alert v-for="w in result.warnings" :key="w" variant="info">{{ w }}</Alert>
          <div class="flex gap-3">
            <SecondaryButton @click="startOver">{{ t('memberImport.importAnother') }}</SecondaryButton>
            <PrimaryButton @click="router.push({ name: 'members-list' })">{{ t('memberImport.toList') }}</PrimaryButton>
          </div>
        </SuccessContainer>
      </template>
    </div>
  </ViewContent>
</template>
