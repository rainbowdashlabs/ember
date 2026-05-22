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
interface ContactPreview { name: string; phone: string; email: string }
interface MemberPreview {
  firstName: string; lastName: string; email: string; group: string
  profileFields: Record<string, string>; contacts: ContactPreview[]
}
interface PreviewResult { members: MemberPreview[]; warnings: string[] }
interface ImportResult { membersCreated: number; managersCreated: number; managersLinked: number; groupsAssigned: number; profileFieldsSet: number; warnings: string[] }

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
  // Dynamic manager targets
  for (let i = 1; i <= managerCount.value; i++) {
    const g = t('memberImport.groupManager', { n: i })
    opts.push({ value: `manager:${i}:firstName`, label: t('memberImport.managerFirstName'), group: g })
    opts.push({ value: `manager:${i}:lastName`, label: t('memberImport.managerLastName'), group: g })
    opts.push({ value: `manager:${i}:phone`, label: t('memberImport.managerPhone'), group: g })
    opts.push({ value: `manager:${i}:email`, label: t('memberImport.managerEmail'), group: g })
  }
  // Profile fields grouped by scope
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

    // Auto-guess mappings
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

  // Manager columns
  for (let i = 1; i <= managerCount.value; i++) {
    if (h.includes(`kontakt ${i}`) || h.includes(`contact ${i}`)) return `manager:${i}:firstName`
    if (h === `email ${i}`) return `manager:${i}:email`
  }
  // Phone columns with numbers
  const phoneMatch = h.match(/(?:telefon|phone).*?(\d)/)
  if (phoneMatch) {
    const n = parseInt(phoneMatch[1])
    if (n >= 1 && n <= managerCount.value) return `manager:${n}:phone`
  }

  // Try matching profile fields by name
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

function isSplit(index: number): boolean {
  return mappings.value[index].splitChar !== ''
}

function getSplitSiblings(csvColumn: string): number[] {
  return mappings.value
    .map((m, i) => m.csvColumn === csvColumn && m.splitChar !== '' ? i : -1)
    .filter(i => i >= 0)
}

function splitColumn(index: number) {
  const m = mappings.value[index]
  const char = ' '
  // Replace original with two split entries
  const part1: ColumnMapping = { ...m, splitChar: char, splitIndex: 0, target: 'skip' }
  const part2: ColumnMapping = { ...m, splitChar: char, splitIndex: 1, target: 'skip' }
  mappings.value.splice(index, 1, part1, part2)
}

function unsplitColumn(index: number) {
  const m = mappings.value[index]
  const siblings = getSplitSiblings(m.csvColumn)
  // Remove all split entries for this column and insert a single unsplit entry
  const original: ColumnMapping = { csvColumn: m.csvColumn, target: 'skip', mergeOrder: m.mergeOrder, mergeSeparator: ' ', valueMap: {}, splitChar: '', splitIndex: 0 }
  // Remove from last to first to keep indices stable
  for (let i = siblings.length - 1; i >= 0; i--) {
    mappings.value.splice(siblings[i], 1)
  }
  mappings.value.splice(siblings[0], 0, original)
}

function addSplitPart(index: number) {
  const m = mappings.value[index]
  const siblings = getSplitSiblings(m.csvColumn)
  const nextIndex = siblings.length
  const newPart: ColumnMapping = { csvColumn: m.csvColumn, splitChar: m.splitChar, splitIndex: nextIndex, target: 'skip', mergeOrder: m.mergeOrder, mergeSeparator: ' ', valueMap: {} }
  mappings.value.splice(siblings[siblings.length - 1] + 1, 0, newPart)
}

function updateSplitChar(csvColumn: string, char: string) {
  for (let i = 0; i < mappings.value.length; i++) {
    if (mappings.value[i].csvColumn === csvColumn && mappings.value[i].splitChar !== '') {
      mappings.value[i] = { ...mappings.value[i], splitChar: char }
    }
  }
}

function getSplitPreview(mapping: ColumnMapping): string[] {
  if (!mapping.splitChar) return []
  const colIdx = headers.value.indexOf(mapping.csvColumn)
  if (colIdx < 0) return []
  return sampleRows.value.map(row => {
    const val = row[colIdx]?.trim() ?? ''
    if (!val) return ''
    const parts = val.split(mapping.splitChar)
    const idx = mapping.splitIndex < 0 ? parts.length + mapping.splitIndex : mapping.splitIndex
    return parts[idx]?.trim() ?? ''
  }).filter(v => v)
}

function needsValueMap(mapping: ColumnMapping): boolean {
  if (!mapping.target.startsWith('field:')) return false
  const fieldId = parseInt(mapping.target.substring(6))
  const field = fields.value.find(f => f.id === fieldId)
  return field?.fieldType === 'boolean' || field?.fieldType === 'enum'
}

const editingValueMapIndex = ref<number | null>(null)
const editingValueMapEntries = ref<Array<{ from: string; to: string }>>([])

function openValueMapEditor(index: number) {
  const m = mappings.value[index]
  const existing = Object.entries(m.valueMap || {})
  editingValueMapEntries.value = existing.length > 0
    ? existing.map(([from, to]) => ({ from, to }))
    : getSampleValues(index).map(v => ({ from: v, to: '' }))
  editingValueMapIndex.value = index
}

function saveValueMap() {
  if (editingValueMapIndex.value === null) return
  const map: Record<string, string> = {}
  for (const e of editingValueMapEntries.value) {
    if (e.from && e.to) map[e.from] = e.to
  }
  mappings.value[editingValueMapIndex.value] = { ...mappings.value[editingValueMapIndex.value], valueMap: map }
  editingValueMapIndex.value = null
}

function addValueMapEntry() {
  editingValueMapEntries.value.push({ from: '', to: '' })
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
        <SecondaryButton @click="router.push({ name: 'members-create' })">
          <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-2" />
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
        <NeutralContainer class="space-y-2 cursor-pointer hover:border-primary transition-colors" @click="router.push({ name: 'members-import-team' })">
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'user-shield']" class="text-primary" />
            <span class="font-semibold">{{ t('teamImport.modeTeam') }}</span>
          </div>
          <p class="text-sm text-(--text-muted)">{{ t('teamImport.modeTeamHint') }}</p>
        </NeutralContainer>
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
          <div class="flex items-center gap-6 flex-wrap">
            <div class="flex items-center gap-2">
              <label class="text-sm font-medium">{{ t('memberImport.separator') }}</label>
              <SelectInput v-model="separator" class="w-20">
                <option value=";">;</option>
                <option value=",">,</option>
                <option value="&#9;">Tab</option>
              </SelectInput>
            </div>
            <div class="flex items-center gap-2">
              <label class="text-sm font-medium">{{ t('memberImport.managerCountLabel') }}</label>
              <SelectInput :model-value="String(managerCount)" @update:model-value="managerCount = Number($event)" class="w-20">
                <option value="0">0</option>
                <option value="1">1</option>
                <option value="2">2</option>
                <option value="3">3</option>
                <option value="4">4</option>
              </SelectInput>
            </div>
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

          <details class="text-sm">
            <summary class="cursor-pointer text-primary font-medium">{{ t('memberImport.helpTitle') }}</summary>
            <div class="mt-2 space-y-3 text-(--text-muted) bg-bg-light-accent/10 dark:bg-bg-dark-accent/10 rounded-lg p-4">
              <div>
                <p class="font-medium text-[var(--text)]">{{ t('memberImport.helpMergeTitle') }}</p>
                <p>{{ t('memberImport.helpMerge') }}</p>
              </div>
              <div>
                <p class="font-medium text-[var(--text)]">{{ t('memberImport.helpSplitTitle') }}</p>
                <p>{{ t('memberImport.helpSplit') }}</p>
              </div>
              <div>
                <p class="font-medium text-[var(--text)]">{{ t('memberImport.helpValueMapTitle') }}</p>
                <p>{{ t('memberImport.helpValueMap') }}</p>
              </div>
            </div>
          </details>

          <div class="space-y-2">
            <div v-for="(m, i) in mappings" :key="i"
                 class="rounded-lg px-3 py-2"
                 :class="m.target === 'skip' ? 'opacity-50' : 'bg-bg-light-accent/20 dark:bg-bg-dark-accent/20'">
              <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 items-center">
                <!-- Left: column name + sample values -->
                <div>
                  <div class="flex items-center gap-2">
                    <span class="font-medium text-sm">
                      {{ m.csvColumn }}
                      <span v-if="isSplit(i)" class="text-xs text-primary font-normal ml-1">
                        ({{ t('memberImport.splitPart') }} {{ m.splitIndex + 1 }})
                      </span>
                    </span>
                    <!-- Split / unsplit button -->
                    <button v-if="!isSplit(i)" class="text-(--text-muted) hover:text-primary cursor-pointer" :title="t('memberImport.splitAction')" @click="splitColumn(i)">
                      <font-awesome-icon :icon="['fas', 'scissors']" class="h-3 w-3" />
                    </button>
                    <template v-else>
                      <button class="text-(--text-muted) hover:text-primary cursor-pointer" :title="t('memberImport.addSplitPart')" @click="addSplitPart(i)">
                        <font-awesome-icon :icon="['fas', 'plus']" class="h-3 w-3" />
                      </button>
                      <button class="text-(--text-muted) hover:text-error cursor-pointer" :title="t('memberImport.unsplit')" @click="unsplitColumn(i)">
                        <font-awesome-icon :icon="['fas', 'xmark']" class="h-3 w-3" />
                      </button>
                    </template>
                  </div>
                  <div class="text-xs text-(--text-muted) truncate">
                    <template v-if="isSplit(i)">
                      {{ getSplitPreview(m).join(', ') || '—' }}
                    </template>
                    <template v-else>
                      {{ getSampleValues(headers.indexOf(m.csvColumn)).join(', ') || '—' }}
                    </template>
                  </div>
                  <!-- Split char config (shown on first split sibling only) -->
                  <div v-if="isSplit(i) && getSplitSiblings(m.csvColumn)[0] === i" class="flex items-center gap-2 mt-1 text-xs">
                    <label class="text-(--text-muted)">{{ t('memberImport.splitChar') }}:</label>
                    <input type="text" :value="m.splitChar" class="w-12 px-1 py-0.5 rounded border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark text-center text-xs" placeholder="' '"
                      @input="updateSplitChar(m.csvColumn, ($event.target as HTMLInputElement).value)" />
                  </div>
                </div>
                <!-- Right: target dropdown + value map -->
                <div class="sm:col-span-2 flex items-center gap-2">
                  <SelectInput :model-value="m.target" class="flex-1" @update:model-value="mappings[i] = { ...m, target: $event as string }">
                    <option value="skip">{{ t('memberImport.targetSkip') }}</option>
                    <optgroup :label="t('memberImport.groupMember')">
                      <option v-for="opt in targetOptions.filter(o => o.group === t('memberImport.groupMember'))" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                    </optgroup>
                    <optgroup v-for="mi in managerCount" :key="'mgr'+mi" :label="t('memberImport.groupManager', { n: mi })">
                      <option v-for="opt in targetOptions.filter(o => o.group === t('memberImport.groupManager', { n: mi }))" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                    </optgroup>
                    <optgroup v-for="sg in fieldScopeGroups" :key="sg" :label="sg">
                      <option v-for="opt in targetOptions.filter(o => o.group === sg)" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                    </optgroup>
                  </SelectInput>
                  <button v-if="needsValueMap(m)" class="text-xs text-primary hover:underline cursor-pointer whitespace-nowrap" @click="openValueMapEditor(i)">
                    {{ Object.keys(m.valueMap || {}).length > 0 ? t('memberImport.editMap') : t('memberImport.addMap') }}
                  </button>
                </div>
              </div>
              <!-- Merge indicator -->
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
              <!-- Value map summary -->
              <div v-if="Object.keys(m.valueMap || {}).length > 0" class="mt-1 text-xs text-(--text-muted)">
                <span v-for="(to, from) in m.valueMap" :key="from" class="mr-2">{{ from }} → {{ to }}</span>
              </div>
            </div>
          </div>

          <!-- Value Map Editor Modal -->
          <div v-if="editingValueMapIndex !== null" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="editingValueMapIndex = null">
            <div class="bg-bg-light dark:bg-bg-dark rounded-lg p-6 w-full max-w-md space-y-4 shadow-lg">
              <SubHeader>{{ t('memberImport.valueMapTitle') }}</SubHeader>
              <p class="text-xs text-(--text-muted)">{{ t('memberImport.valueMapHint') }}</p>
              <div class="space-y-2">
                <div v-for="(entry, ei) in editingValueMapEntries" :key="ei" class="grid grid-cols-2 gap-2">
                  <input v-model="entry.from" :placeholder="t('memberImport.csvValue')" class="px-2 py-1.5 rounded border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark text-sm" />
                  <input v-model="entry.to" :placeholder="t('memberImport.targetValue')" class="px-2 py-1.5 rounded border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark text-sm" />
                </div>
              </div>
              <SecondaryButton @click="addValueMapEntry">+ {{ t('memberImport.addRow') }}</SecondaryButton>
              <div class="flex justify-end gap-3">
                <SecondaryButton @click="editingValueMapIndex = null">{{ t('common.cancel') }}</SecondaryButton>
                <PrimaryButton @click="saveValueMap">{{ t('common.save') }}</PrimaryButton>
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
          <SubHeader>{{ t('memberImport.previewTitle', { count: preview.members.length }) }}</SubHeader>
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent">
                  <th class="text-left py-2 px-2">{{ t('memberImport.name') }}</th>
                  <th class="text-left py-2 px-2">{{ t('memberImport.email') }}</th>
                  <th class="text-left py-2 px-2">{{ t('memberImport.group') }}</th>
                  <th class="text-left py-2 px-2">{{ t('memberImport.fields') }}</th>
                  <th class="text-left py-2 px-2">{{ t('memberImport.contacts') }}</th>
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
                  <td class="py-2 px-2 text-xs">
                    <div v-for="c in m.contacts" :key="c.name">
                      {{ c.name }} <span v-if="c.email" class="text-(--text-muted)">({{ c.email }})</span>
                    </div>
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
          <div class="grid grid-cols-2 sm:grid-cols-5 gap-3 text-center">
            <div><p class="text-xl font-bold">{{ result.membersCreated }}</p><p class="text-xs text-(--text-muted)">{{ t('memberImport.membersCreated') }}</p></div>
            <div><p class="text-xl font-bold">{{ result.managersCreated }}</p><p class="text-xs text-(--text-muted)">{{ t('memberImport.managersCreated') }}</p></div>
            <div><p class="text-xl font-bold">{{ result.managersLinked }}</p><p class="text-xs text-(--text-muted)">{{ t('memberImport.managersLinked') }}</p></div>
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
