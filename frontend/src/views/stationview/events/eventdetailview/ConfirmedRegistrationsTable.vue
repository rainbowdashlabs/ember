/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ActionsMenu from '@/components/button/ActionsMenu.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import RadioInput from '@/components/input/toggle/RadioInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import ColumnFilterModal from '@/components/table/ColumnFilterModal.vue'
import ColumnPickerButton from '@/components/table/ColumnPickerButton.vue'
import DataTable from '@/components/table/DataTable.vue'
import HeaderFilterCell from '@/components/table/HeaderFilterCell.vue'
import Td from '@/components/table/Td.vue'
import Th from '@/components/table/Th.vue'
import type {ColumnPickerOption} from '@/components/table/columns'
import {memberTable as api} from '@/api'
import {RegistrationStatus} from '@/api/events'
import type {EventRegistrationEntry} from '@/api/events'
import type {MemberTable, MemberTableColumn, MemberTableHeader} from '@/api/memberTable'
import {StationPermission, StationUserTypeLabels, type StationUserTypeName} from '@/api/types'
import {useColumnFilters} from '@/composables/useColumnFilters'
import {useMemberTableColumns} from '@/composables/useMemberTableColumns'
import {useSession} from '@/composables/useSession'
import {sortIconFor, type SortDirection} from '@/composables/useSortable'
import {saveBlob} from '@/util/downloadAuthed'

const {t} = useI18n()
const {hasPermission} = useSession()

const props = defineProps<{
  eventId: number
  /** The heading of the block, which the controls share a line with. */
  title: string
  /** The evening these sign-ups belong to, because a registration belongs to one. */
  effectiveDate: string | null
  /** Every sign-up for the evening, which is where a row's status and its accept button come from. */
  entries: EventRegistrationEntry[]
}>()

/**
 * Whether the confirmed sign-ups are shown as a table rather than as the cards they have always been.
 *
 * <p>Off to begin with. The cards are what everybody knows and they hold more per person than a row
 * can; the table is for the evening somebody wants one line each and a sheet to carry.
 */
const asTable = defineModel<boolean>('asTable', {required: true})

const emit = defineEmits<{
  accept: [registrationId: number]
}>()

/**
 * Whether this reader handles the registrations, which guards the whole table.
 *
 * <p>The server draws it only for whoever holds that permission, so nobody else is shown a menu
 * whose every entry would be refused. The row's accept button stands behind the same right.
 */
const canDecide = computed(() => hasPermission(StationPermission.EVENT_REGISTRATION))

const {offered, selected, columns, keyOf, toggle} = useMemberTableColumns()
const table = ref<MemberTable | null>(null)
const exporting = ref(false)
const format = ref<'csv' | 'pdf'>('csv')

const STATUS_LABEL_KEYS: Record<string, string> = {
  [RegistrationStatus.ACCEPTED]: 'eventsUpcoming.statusAccepted',
  [RegistrationStatus.PENDING]: 'eventsUpcoming.statusPending',
  [RegistrationStatus.DENIED]: 'eventsUpcoming.statusDenied',
  [RegistrationStatus.DECLINED]: 'eventsUpcoming.statusDeclined',
  [RegistrationStatus.WITHDRAWN]: 'eventsUpcoming.statusWithdrawn',
}

function statusLabel(status: string): string {
  const key = STATUS_LABEL_KEYS[status]
  return key ? t(key) : status
}

/**
 * A cell as the screen should read it, which is words where the table holds a token.
 *
 * <p>The drawn table carries what a thing is rather than what it is called; the file exports are
 * worded by the server for the same reason, and the screen words its own cells here. The filters
 * compare these words against themselves, so they cannot fall out of step with what is shown.
 */
function wordedCell(index: number, value: string): string {
  const column = table.value?.columns[index]
  if (!column || column.kind !== 'BUILTIN' || value === '') return value
  if (column.key === 'registrationStatus') return statusLabel(value)
  if (column.key === 'memberType') return StationUserTypeLabels[value as StationUserTypeName] ?? value
  return value
}

const byMember = computed(() => new Map(props.entries.map(entry => [entry.memberId, entry])))

/** Every row beside its worded cells and the sign-up it stands for. */
const wordedRows = computed(() => {
  if (!table.value) return []
  return table.value.rows.map(row => ({
    row,
    values: row.values.map((value, index) => wordedCell(index, value ?? '')),
    entry: byMember.value.get(row.memberId) ?? null,
  }))
})

/** One name per column that survives the columns being picked and unpicked. */
function columnIdOf(column: MemberTableHeader): string {
  return `${column.kind}:${column.key ?? column.fieldId}`
}

const columnIds = computed(() => new Map(
    (table.value?.columns ?? []).map((column, index) => [columnIdOf(column), index])))

/** Where the name stands in a row, so that cell can carry the picture beside it. */
const nameIndex = computed(() => columnIds.value.get('BUILTIN:name') ?? -1)

const DAY_PATTERN = /^\d{2}\.\d{2}\.\d{4}$/

/** The columns whose every written value reads as a day, which filter by day rather than by word. */
const dateColumns = computed<Set<string>>(() => {
  const dates = new Set<string>()
  for (const [id, index] of columnIds.value) {
    const values = wordedRows.value.map(({values}) => values[index] ?? '').filter(value => value !== '')
    if (values.length > 0 && values.every(value => DAY_PATTERN.test(value))) dates.add(id)
  }
  return dates
})

function isoOf(value: string): string {
  const [day, month, year] = value.split('.')
  return `${year}-${month}-${day}`
}

/** The row's value in one column as the filter compares it: the ISO day where days are filtered. */
function cellFilterValues(id: string, values: string[]): string[] {
  const index = columnIds.value.get(id)
  if (index === undefined) return []
  const value = values[index] ?? ''
  if (value === '') return []
  return [dateColumns.value.has(id) ? isoOf(value) : value]
}

/**
 * The shared per-column filters, opening on the confirmed sign-ups.
 *
 * <p>A sheet of who is coming is what this table is for, so the status column starts narrowed to
 * the confirmed ones; clearing that filter shows everybody standing on the evening's list.
 */
const filters = useColumnFilters<string>({
  kindOf: id => (dateColumns.value.has(id) ? 'date' : 'text'),
  distinctValues: id => {
    const cells = wordedRows.value.flatMap(({values}) => cellFilterValues(id, values))
    return [...new Set(cells)].toSorted()
  },
  initial: new Map([['BUILTIN:registrationStatus', new Set([t('eventsUpcoming.statusAccepted')])]]),
})

const sortIndex = ref<number | null>(null)
const sortDirection = ref<SortDirection>('asc')

function toggleSort(index: number) {
  if (sortIndex.value === index) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortIndex.value = index
    sortDirection.value = 'asc'
  }
}

function sortIcon(index: number): string {
  return sortIconFor(sortIndex.value === index, sortDirection.value)
}

/** The rows as shown: worded, filtered and sorted the way the header says. */
const shownRows = computed(() => {
  const rows = wordedRows.value.filter(({values}) => filters.rowPasses(id => cellFilterValues(id, values)))
  if (sortIndex.value === null) return rows
  const index = sortIndex.value
  const factor = sortDirection.value === 'asc' ? 1 : -1
  return rows.toSorted((a, b) => factor * (a.values[index] ?? '')
      .localeCompare(b.values[index] ?? '', undefined, {sensitivity: 'base', numeric: true}))
})

const day = computed(() => props.effectiveDate ?? new Date().toISOString().slice(0, 10))

/** The chosen columns behind the name and the status, which are always there and never ticked. */
const drawnColumns = computed<MemberTableColumn[]>(() => [
  {kind: 'BUILTIN', key: 'name', fieldId: null},
  {kind: 'BUILTIN', key: 'registrationStatus', fieldId: null},
  ...columns.value,
])

const pickerOptions = computed<ColumnPickerOption[]>(() =>
    offered.value.map(column => ({
      key: keyOf(column),
      label: column.label,
      visible: selected.value.has(keyOf(column)),
    })))

/**
 * What may go on the table, asked for once.
 *
 * <p>It comes from the station holding the appointment, which is the only place that knows what
 * this reader may read. The name and the status are always drawn, so neither is offered as a column
 * to tick: a list of people with the names left off is not a thing anybody wants.
 */
async function ensureOffered() {
  if (offered.value.length > 0) return
  const available = await api.listRegistrationColumns(props.eventId)
  offered.value = [
    ...available.member.filter(column => column.key !== 'name' && column.key !== 'registrationStatus'),
    ...available.questions.map(question => ({
      label: question.label,
      kind: 'REGISTRATION_FIELD' as const,
      key: null,
      fieldId: question.fieldId,
    })),
  ]
}

async function draw() {
  table.value = await api.drawRegistrationTable(props.eventId, day.value, drawnColumns.value)
}

/**
 * Draws the table whenever it is shown, and again whenever what it should say changes.
 *
 * <p>Fetched on being shown rather than once at the start: somebody confirms a place, switches to
 * the table and must see them on it. The cards behind it are reloaded by the screen for the same
 * reason, and a table that answered from what it happened to be holding would disagree with them.
 */
watch(
    [asTable, () => props.eventId, day, () => props.entries.length],
    async () => {
      if (!asTable.value) return
      await ensureOffered()
      await draw()
    },
    {immediate: true},
)

watch(selected, () => {
  if (asTable.value) draw()
})

/**
 * Asks the station for the same table as a file.
 *
 * <p>Asked for rather than built here, so what somebody carries away and what they were looking at
 * cannot disagree about which columns this reader may see. What the filters are hiding is not
 * written down either: the file is the whole list.
 */
async function download() {
  const file = await api.exportRegistrationTable(props.eventId, day.value, drawnColumns.value, format.value)
  saveBlob(file, `anmeldungen.${format.value}`)
  exporting.value = false
}
</script>

<template>
  <div class="space-y-2">
    <div class="flex items-center justify-between gap-2">
      <SubHeader>{{ title }}</SubHeader>
      <div v-if="canDecide" class="flex items-center gap-2">
        <ColumnPickerButton
            v-if="asTable"
            :options="pickerOptions"
            :empty-label="t('common.noOptions')"
            @toggle="toggle(String($event))"
        />
        <ActionsMenu :label="t('memberTable.menu')" test-id="registration-table-menu">
          <DropdownMenuItem
              :icon="['fas', 'table-list']"
              data-testid="registration-table-toggle"
              @click="asTable = !asTable"
          >
            {{ asTable ? t('memberTable.asCards') : t('memberTable.asTable') }}
          </DropdownMenuItem>
          <DropdownMenuItem
              :icon="['fas', 'file-export']"
              data-testid="registration-table-export"
              @click="exporting = true"
          >
            {{ t('memberTable.export') }}
          </DropdownMenuItem>
        </ActionsMenu>
      </div>
    </div>

    <Modal v-model="exporting">
      <div class="space-y-4">
        <SubHeader>{{ t('memberTable.export') }}</SubHeader>
        <MutedText tag="p" size="sm">{{ t('memberTable.exportHint') }}</MutedText>
        <div class="space-y-2">
          <FieldLabel>{{ t('memberTable.format') }}</FieldLabel>
          <div class="flex items-center gap-4">
            <FieldLabel inline class="cursor-pointer">
              <RadioInput v-model="format" value="csv"/>
              {{ t('memberTable.csv') }}
            </FieldLabel>
            <FieldLabel inline class="cursor-pointer">
              <RadioInput v-model="format" value="pdf"/>
              {{ t('memberTable.pdf') }}
            </FieldLabel>
          </div>
        </div>
        <ButtonRow pair align="end">
          <SecondaryButton @click="exporting = false">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :icon="['fas', 'download']" data-testid="registration-table-download" @click="download">
            {{ t('memberTable.export') }}
          </PrimaryButton>
        </ButtonRow>
      </div>
    </Modal>

    <ColumnFilterModal
        v-model="filters.modalOpen"
        :column-label="filters.modalLabel"
        :values="filters.modalValues"
        :selected-values="filters.modalSelected"
        :include-empty="filters.modalIncludeEmpty"
        :field-kind="filters.modalKind"
        @apply="filters.apply"
    />

    <template v-if="asTable && table">
      <MutedText v-if="shownRows.length === 0" tag="p" size="sm">
        {{ t('memberTable.nobody') }}
      </MutedText>

      <DataTable v-else plain data-testid="confirmed-registrations-table">
        <template #head>
          <Th v-for="(column, index) in table.columns" :key="columnIdOf(column)">
            <HeaderFilterCell
                :label="column.label"
                :sort-icon="sortIcon(index)"
                :has-filter="filters.hasActive(columnIdOf(column))"
                show-sort
                show-filter
                @sort="toggleSort(index)"
                @filter="filters.open(columnIdOf(column), column.label)"
            />
          </Th>
          <Th v-if="canDecide" align="right"/>
        </template>
        <tr v-for="{row, values, entry} in shownRows" :key="row.memberId">
          <Td v-for="(value, index) in values" :key="index">
            <MemberName v-if="index === nameIndex && entry?.memberIdentity" :identity="entry.memberIdentity">
              {{ value }}
            </MemberName>
            <template v-else>{{ value }}</template>
          </Td>
          <Td v-if="canDecide" align="right">
            <PrimaryButton
                v-if="entry && entry.status !== RegistrationStatus.ACCEPTED"
                compact
                :data-testid="`accept-row-${entry.id}`"
                @click="emit('accept', entry.id)"
            >
              <font-awesome-icon :icon="['fas', 'check']"/>
            </PrimaryButton>
          </Td>
        </tr>
      </DataTable>
    </template>
  </div>
</template>
