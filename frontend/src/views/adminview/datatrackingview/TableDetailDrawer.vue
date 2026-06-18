/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {dataTracking} from '@/api'
import type {
  ColumnEntry,
  DeletionStrategy,
  GdprDeletionContext,
  GdprExportContext,
  TableEntry,
  TrackingStatusName,
  TransferContext,
} from '@/api/dataTracking'
import {TrackingStatus} from '@/api/dataTracking'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Alert from '@/components/feedback/Alert.vue'
import StatusBadge from './StatusBadge.vue'

const props = defineProps<{
  name: string
  entry: TableEntry
}>()
const emit = defineEmits<{
  (e: 'close'): void
  (e: 'updated', name: string, entry: TableEntry): void
}>()

const {t} = useI18n()

// Editable copies — populated from props when the drawer opens or the selected table changes.
const stationTransfer = ref<TransferContext>({...props.entry.stationTransfer})
const gdprExport = ref<GdprExportContext>({...props.entry.gdprExport})
const gdprDeletion = ref<GdprDeletionContext>({...props.entry.gdprDeletion})
const columns = ref<ColumnEntry[]>(props.entry.columns.map(c => ({...c})))
const error = ref('')

watch(
    () => props.entry,
    e => {
      stationTransfer.value = {...e.stationTransfer}
      gdprExport.value = {...e.gdprExport}
      gdprDeletion.value = {...e.gdprDeletion}
      columns.value = e.columns.map(c => ({...c}))
      error.value = ''
    },
)

const allColumnsVerified = computed(() => columns.value.every(c => c.verified))

const columnOptions = computed(() =>
    columns.value.map(c => ({value: c.name, label: c.name, group: c.type})),
)

const transferIgnoredColumns = computed({
  get: () => stationTransfer.value.ignoredColumns ?? [],
  set: (v: string[]) => {
    stationTransfer.value.ignoredColumns = [...v]
  },
})
const gdprIgnoredColumns = computed({
  get: () => gdprExport.value.ignoredColumns ?? [],
  set: (v: string[]) => {
    gdprExport.value.ignoredColumns = [...v]
  },
})

const statuses: TrackingStatusName[] = [
  TrackingStatus.TRACKED,
  TrackingStatus.IGNORED,
  TrackingStatus.UNVERIFIED,
]

// All deletion strategies the backend recognises. Keep in sync with dev.chojo.ember.tracking.Strategy.
const STRATEGIES = [
  'CASCADE',
  'DELETE_EXPLICIT',
  'ANONYMIZE',
  'NULL',
  'RETAIN',
  'RETAIN_UNLINKED',
  'NOT_APPLICABLE',
] as const

function ensureDeletionStrategies(): DeletionStrategy[] {
  if (!gdprDeletion.value.strategies) gdprDeletion.value.strategies = []
  return gdprDeletion.value.strategies
}

function addDeletionStrategy() {
  const arr = ensureDeletionStrategies()
  // Default to the first column the table actually has so the row is meaningful out of the gate.
  const firstColumn = columns.value[0]?.name ?? ''
  arr.push({column: firstColumn, strategy: 'NULL', reason: '', legalBasis: null})
}

function removeDeletionStrategy(index: number) {
  const arr = ensureDeletionStrategies()
  arr.splice(index, 1)
}

async function save() {
  error.value = ''
  try {
    const overrides: Record<string, boolean> = {}
    for (let i = 0; i < columns.value.length; i++) {
      const before = props.entry.columns[i]
      const after = columns.value[i]
      if (before && before.verified !== after.verified) overrides[after.name] = after.verified
    }
    const updated = await dataTracking.updateDataTrackingTable(props.name, {
      columnVerified: Object.keys(overrides).length > 0 ? overrides : undefined,
      stationTransfer: stationTransfer.value,
      gdprExport: gdprExport.value,
      gdprDeletion: gdprDeletion.value,
    })
    emit('updated', props.name, updated)
    emit('close')
  } catch (e) {
    error.value = (e as Error).message || t('common.error')
    throw e
  }
}

/** Returns the foreign-key descriptor for a column, if any. Used to render the inline key icon. */
function foreignKeyFor(column: string) {
  return (props.entry.foreignKeys ?? []).find(fk => fk.column === column)
}

function foreignKeyTooltip(column: string): string {
  const fk = foreignKeyFor(column)
  if (!fk) return ''
  return `FK → ${fk.refTable}.${fk.refColumn} (${fk.onDelete})`
}

async function verifyAll() {
  error.value = ''
  try {
    const updated = await dataTracking.verifyAllColumns(props.name)
    columns.value = updated.columns.map(c => ({...c}))
    emit('updated', props.name, updated)
  } catch (e) {
    error.value = (e as Error).message || t('common.error')
  }
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex">
    <div class="flex-1 bg-black/40" @click="emit('close')"/>
    <div
        class="w-full max-w-2xl bg-(--bg) border-l border-(--border) shadow-2xl overflow-y-auto"
    >
      <div class="sticky top-0 z-10 bg-(--bg) border-b border-(--border) p-4 flex items-center justify-between gap-2">
        <div class="min-w-0 flex-1">
          <SubHeader class="!text-xl font-mono !mb-0 truncate">{{ name }}</SubHeader>
          <!-- Mirror of the PG COMMENT ON TABLE text (read-only — source of truth is the patch file). -->
          <p
              v-if="entry.description"
              class="mt-1 text-sm text-(--text-muted) italic"
          >
            {{ entry.description }}
          </p>
          <div class="flex items-center gap-2 mt-1 text-xs text-(--text-muted) flex-wrap">
            <span>{{ entry.feature ?? '—' }}</span>
            <span>·</span>
            <span>{{ entry.scope ?? '—' }}</span>
            <span>·</span>
            <span class="font-mono">{{ entry.tableHash.slice(0, 20) }}…</span>
          </div>
        </div>
        <IconButton :icon="['fas', 'xmark']" :label="t('common.close')" @click="emit('close')"/>
      </div>

      <div class="p-4 space-y-6">
        <Alert v-if="error" variant="error">{{ error }}</Alert>

        <!-- Schema layout -->
        <section>
          <SectionHeader class="!text-base">{{ t('adminDataTracking.detail.columns') }}</SectionHeader>
          <div class="rounded-theme border border-(--border)">
            <div
                v-for="col in columns"
                :key="col.name"
                class="px-3 py-1.5 border-b border-(--border) last:border-b-0"
            >
              <div class="flex items-center justify-between gap-2">
                <div class="font-mono text-sm flex items-center gap-2 min-w-0 flex-1">
                  <!-- Key icon when the column is the source of a foreign key. Tooltip carries the FK target. -->
                  <font-awesome-icon
                      v-if="foreignKeyFor(col.name)"
                      :icon="['fas', 'key']"
                      class="text-(--text-muted)"
                      :title="foreignKeyTooltip(col.name)"
                  />
                  <span class="font-semibold truncate">{{ col.name }}</span>
                  <span class="text-(--text-muted)">{{ col.type }}{{ col.nullable ? '?' : '' }}</span>
                </div>
                <ToggleInput v-model="col.verified" :label="t('adminDataTracking.detail.verified')"/>
              </div>
              <!-- Mirror of the PG COMMENT ON COLUMN text — appears under the column header when present. -->
              <p
                  v-if="col.description"
                  class="text-xs text-(--text-muted) italic pl-1 mt-0.5"
              >
                {{ col.description }}
              </p>
            </div>
          </div>
          <div class="mt-2 flex items-center justify-between">
            <span class="text-xs text-(--text-muted)">
              {{ columns.filter(c => c.verified).length }} / {{ columns.length }}
              {{ t('adminDataTracking.detail.verifiedShort') }}
            </span>
            <SuccessButton
                v-if="!allColumnsVerified"
                :icon="['fas', 'check-double']"
                @click="verifyAll"
            >
              {{ t('adminDataTracking.detail.verifyAll') }}
            </SuccessButton>
          </div>
        </section>

        <!-- Foreign keys + lookups overview (read-only — these are schema-derived) -->
        <section v-if="entry.foreignKeys?.length">
          <SectionHeader class="!text-base">{{ t('adminDataTracking.detail.foreignKeys') }}</SectionHeader>
          <ul class="text-xs font-mono space-y-1">
            <li v-for="fk in entry.foreignKeys" :key="fk.column">
              <span class="font-semibold">{{ fk.column }}</span> →
              <span>{{ fk.refTable }}.{{ fk.refColumn }}</span>
              <span class="ml-1 text-(--text-muted)">({{ fk.onDelete }})</span>
            </li>
          </ul>
        </section>

        <section v-if="entry.lookups?.length">
          <SectionHeader class="!text-base">{{ t('adminDataTracking.detail.lookups') }}</SectionHeader>
          <ul class="text-xs font-mono space-y-1">
            <li v-for="lk in entry.lookups" :key="lk.emitAs">
              <span class="font-semibold">{{ lk.emitAs }}</span>
              ← {{ lk.via }} → {{ lk.pick }}
            </li>
          </ul>
        </section>

        <section v-if="entry.customScope">
          <SectionHeader class="!text-base">{{ t('adminDataTracking.detail.customScope') }}</SectionHeader>
          <pre class="text-xs font-mono bg-(--bg-accent) rounded-theme p-3 overflow-x-auto">{{ JSON.stringify(entry.customScope, null, 2) }}</pre>
        </section>

        <!-- Station transfer editor -->
        <section>
          <div class="flex items-center justify-between">
            <SectionHeader class="!text-base">{{ t('adminDataTracking.stationTransfer') }}</SectionHeader>
            <StatusBadge :status="stationTransfer.status"/>
          </div>
          <div class="space-y-2 mt-2">
            <SelectInput v-model="stationTransfer.status">
              <option v-for="s in statuses" :key="s" :value="s">{{ s }}</option>
            </SelectInput>
            <TextInput
                v-if="stationTransfer.status === 'IGNORED'"
                v-model="stationTransfer.reason"
                :placeholder="t('adminDataTracking.detail.reason')"
            />
            <TextAreaInput
                v-if="stationTransfer.status === 'TRACKED'"
                v-model="stationTransfer.rationale"
                :placeholder="t('adminDataTracking.detail.rationale')"
                :rows="2"
            />
            <!-- Re-check note: the rationale field is reused as a free-form note so a reviewer can
                 leave context for whoever picks the table up next. -->
            <TextAreaInput
                v-if="stationTransfer.status === 'UNVERIFIED'"
                v-model="stationTransfer.rationale"
                :placeholder="t('adminDataTracking.detail.reviewNote')"
                :rows="2"
            />
            <div>
              <label class="block text-xs text-(--text-muted) mb-1">
                {{ t('adminDataTracking.detail.ignoredColumns') }}
              </label>
              <MultiSelectDropdown
                  v-model="transferIgnoredColumns"
                  :options="columnOptions"
                  :placeholder="t('adminDataTracking.detail.ignoredColumnsPlaceholder')"
                  searchable
              />
            </div>
          </div>
        </section>

        <!-- GDPR export editor -->
        <section>
          <div class="flex items-center justify-between">
            <SectionHeader class="!text-base">{{ t('adminDataTracking.gdprExport') }}</SectionHeader>
            <StatusBadge :status="gdprExport.status"/>
          </div>
          <div class="space-y-2 mt-2">
            <SelectInput v-model="gdprExport.status">
              <option v-for="s in statuses" :key="s" :value="s">{{ s }}</option>
            </SelectInput>
            <TextInput
                v-if="gdprExport.status === 'IGNORED'"
                v-model="gdprExport.reason"
                :placeholder="t('adminDataTracking.detail.reason')"
            />
            <TextAreaInput
                v-if="gdprExport.status === 'UNVERIFIED'"
                v-model="gdprExport.reason"
                :placeholder="t('adminDataTracking.detail.reviewNote')"
                :rows="2"
            />
            <div>
              <label class="block text-xs text-(--text-muted) mb-1">
                {{ t('adminDataTracking.detail.ignoredColumns') }}
              </label>
              <MultiSelectDropdown
                  v-model="gdprIgnoredColumns"
                  :options="columnOptions"
                  :placeholder="t('adminDataTracking.detail.ignoredColumnsPlaceholder')"
                  searchable
              />
            </div>
            <div v-if="gdprExport.identityColumns?.length">
              <span class="text-xs text-(--text-muted)">{{ t('adminDataTracking.detail.identityColumns') }}:</span>
              <ul class="text-xs font-mono mt-1 space-y-0.5">
                <li v-for="ic in gdprExport.identityColumns" :key="ic.column">
                  {{ ic.column }} <span class="text-(--text-muted)">({{ ic.type }})</span>
                </li>
              </ul>
            </div>
          </div>
        </section>

        <!-- GDPR deletion editor -->
        <section>
          <div class="flex items-center justify-between">
            <SectionHeader class="!text-base">{{ t('adminDataTracking.gdprDeletion') }}</SectionHeader>
            <StatusBadge :status="gdprDeletion.status"/>
          </div>
          <div class="space-y-2 mt-2">
            <SelectInput v-model="gdprDeletion.status">
              <option v-for="s in statuses" :key="s" :value="s">{{ s }}</option>
            </SelectInput>
            <TextInput
                v-if="gdprDeletion.status === 'IGNORED'"
                v-model="gdprDeletion.reason"
                :placeholder="t('adminDataTracking.detail.reason')"
            />
            <TextAreaInput
                v-if="gdprDeletion.status === 'UNVERIFIED'"
                v-model="gdprDeletion.reason"
                :placeholder="t('adminDataTracking.detail.reviewNote')"
                :rows="2"
            />
            <div>
              <div class="flex items-center justify-between mb-1">
                <span class="text-xs text-(--text-muted)">{{ t('adminDataTracking.detail.strategies') }}</span>
                <SecondaryButton :icon="['fas', 'plus']" @click="addDeletionStrategy">
                  {{ t('adminDataTracking.detail.addStrategy') }}
                </SecondaryButton>
              </div>
              <div
                  v-if="!gdprDeletion.strategies?.length"
                  class="text-xs text-(--text-muted) italic py-2"
              >
                {{ t('adminDataTracking.detail.noStrategies') }}
              </div>
              <div
                  v-for="(s, idx) in gdprDeletion.strategies ?? []"
                  :key="idx"
                  class="rounded-theme border border-(--border) p-2 mb-2 space-y-2"
              >
                <div class="grid grid-cols-1 md:grid-cols-2 gap-2">
                  <div>
                    <label class="block text-xs text-(--text-muted) mb-1">
                      {{ t('adminDataTracking.detail.strategyColumn') }}
                    </label>
                    <SelectInput v-model="s.column">
                      <option v-for="c in columns" :key="c.name" :value="c.name">{{ c.name }}</option>
                    </SelectInput>
                  </div>
                  <div>
                    <label class="block text-xs text-(--text-muted) mb-1">
                      {{ t('adminDataTracking.detail.strategyKind') }}
                    </label>
                    <SelectInput v-model="s.strategy">
                      <option v-for="st in STRATEGIES" :key="st" :value="st">{{ st }}</option>
                    </SelectInput>
                  </div>
                </div>
                <div>
                  <label class="block text-xs text-(--text-muted) mb-1">
                    {{ t('adminDataTracking.detail.reason') }}
                  </label>
                  <TextInput v-model="s.reason"/>
                </div>
                <div v-if="s.strategy === 'RETAIN'">
                  <label class="block text-xs text-(--text-muted) mb-1">
                    {{ t('adminDataTracking.detail.legalBasis') }}
                  </label>
                  <TextInput
                      v-model="s.legalBasis"
                      :placeholder="t('adminDataTracking.detail.legalBasisPlaceholder')"
                  />
                </div>
                <div class="flex justify-end">
                  <DeleteButton @click="removeDeletionStrategy(idx)">
                    {{ t('common.delete') }}
                  </DeleteButton>
                </div>
              </div>
            </div>
          </div>
        </section>

        <div class="sticky bottom-0 bg-(--bg) border-t border-(--border) -mx-4 px-4 py-3 flex items-center gap-2 justify-end">
          <SecondaryButton @click="emit('close')">{{ t('common.cancel') }}</SecondaryButton>
          <SaveButton :action="save"/>
        </div>
      </div>
    </div>
  </div>
</template>
