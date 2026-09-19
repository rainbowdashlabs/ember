/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ActionsMenu from '@/components/button/ActionsMenu.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import ColumnPickerButton from '@/components/table/ColumnPickerButton.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import type {ColumnOption, TableColumn} from '@/components/table/tableColumn'
import {memberTable as api} from '@/api'
import {RegistrationStatus} from '@/api/events'
import type {EventRegistrationEntry} from '@/api/events'
import type {MemberTable, MemberTableColumn, MemberTableHeader} from '@/api/memberTable'
import {StationPermission, StationUserTypeLabels} from '@/api/types'
import {emptyTableState, useDataTable} from '@/composables/useDataTable'
import {useSession} from '@/composables/useSession'
import {saveBlob} from '@/util/downloadAuthed'
import RegistrationTableExport from './RegistrationTableExport.vue'
import {columnOf, keyOf, tableColumnOf, type DrawnRow} from './registrationTableColumns'

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

type Row = DrawnRow<EventRegistrationEntry | null>

/**
 * Whether this reader handles the registrations, which guards the whole table.
 *
 * <p>The server draws it only for whoever holds that permission, so nobody else is shown a menu
 * whose every entry would be refused. The row's accept button stands behind the same right.
 */
const canDecide = computed(() => hasPermission(StationPermission.EVENT_REGISTRATION))

const NAME_KEY = 'b:name'
const STATUS_KEY = 'b:registrationStatus'

const STATUS_LABEL_KEYS: Record<string, string> = {
  [RegistrationStatus.ACCEPTED]: 'eventsUpcoming.statusAccepted',
  [RegistrationStatus.PENDING]: 'eventsUpcoming.statusPending',
  [RegistrationStatus.DENIED]: 'eventsUpcoming.statusDenied',
  [RegistrationStatus.DECLINED]: 'eventsUpcoming.statusDeclined',
  [RegistrationStatus.WITHDRAWN]: 'eventsUpcoming.statusWithdrawn',
}

/** The tokens the table carries for a status or a kind of member, in words. */
const OPTIONS_BY_KEY = computed<Record<string, ColumnOption[]>>(() => ({
  [STATUS_KEY]: Object.entries(STATUS_LABEL_KEYS).map(([value, key]) => ({value, label: t(key)})),
  'b:memberType': Object.entries(StationUserTypeLabels).map(([value, label]) => ({value, label})),
}))

const offered = ref<MemberTableHeader[]>([])
const drawn = ref<MemberTable | null>(null)
const exporting = ref(false)

/**
 * Every column the table may show. The name and the status are always drawn and never offered as
 * columns to tick: a list of people with the names left off is not a thing anybody wants.
 */
const columns = computed<TableColumn<Row>[]>(() => offered.value.map(header => {
  const column = tableColumnOf<EventRegistrationEntry | null>(header, OPTIONS_BY_KEY.value[keyOf(header)])
  const fixed = column.key === NAME_KEY || column.key === STATUS_KEY
  return fixed ? {...column, pinned: true} : column
}))

const byMember = computed(() => new Map(props.entries.map(entry => [entry.memberId, entry])))

const rows = computed<Row[]>(() => {
  const table = drawn.value
  if (!table) return []
  const keys = table.columns.map(keyOf)
  return table.rows.map(row => ({
    memberId: row.memberId,
    cells: new Map(keys.map((key, index) => [key, row.values[index] ?? ''])),
    extra: byMember.value.get(row.memberId) ?? null,
  }))
})

/**
 * The table opens on the confirmed sign-ups, because a sheet of who is coming is what it is for.
 * Clearing that filter shows everybody standing on the evening's list.
 */
const state = ref(emptyTableState(NAME_KEY))
state.value.filters = new Map([[STATUS_KEY, new Set<string>([RegistrationStatus.ACCEPTED])]])

const table = useDataTable<Row>({
  id: 'event-registrations',
  rows,
  columns,
  rowKey: row => row.memberId,
  state,
})

const day = computed(() => props.effectiveDate ?? new Date().toISOString().slice(0, 10))

/** The shown columns as the server names them, the name and the status first. */
const drawnColumns = computed<MemberTableColumn[]>(() => [
  NAME_KEY,
  STATUS_KEY,
  ...table.visibleColumns.map(column => column.key).filter(key => key !== NAME_KEY && key !== STATUS_KEY),
].map(columnOf).filter((column): column is MemberTableColumn => column !== null))

/** What may go on the table, asked for once, from the station holding the appointment. */
async function ensureOffered() {
  if (offered.value.length > 0) return
  const available = await api.listRegistrationColumns(props.eventId)
  offered.value = [
    ...available.member,
    ...available.questions.map(question => ({
      label: question.label,
      kind: 'REGISTRATION_FIELD' as const,
      key: null,
      fieldId: question.fieldId,
      type: question.type,
    })),
  ]
}

async function draw() {
  drawn.value = await api.drawRegistrationTable(props.eventId, day.value, drawnColumns.value)
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

watch(() => drawnColumns.value.map(keyOf).join(), () => {
  if (asTable.value && offered.value.length > 0) draw()
})

/**
 * Asks the station for the same table as a file.
 *
 * <p>Asked for rather than built here, so what somebody carries away and what they were looking at
 * cannot disagree about which columns this reader may see. What the filters are hiding is not
 * written down either: the file is the whole list.
 */
async function download(format: 'csv' | 'pdf') {
  const file = await api.exportRegistrationTable(props.eventId, day.value, drawnColumns.value, format)
  saveBlob(file, `anmeldungen.${format}`)
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
            :options="table.pickerOptions"
            :empty-label="t('common.noOptions')"
            @toggle="table.toggleColumn"
        />
        <ActionsMenu :label="t('memberTable.menu')" test-id="registration-table-menu">
          <DropdownMenuItem :icon="['fas', 'table-list']" data-testid="registration-table-toggle" @click="asTable = !asTable">
            {{ asTable ? t('memberTable.asCards') : t('memberTable.asTable') }}
          </DropdownMenuItem>
          <DropdownMenuItem :icon="['fas', 'file-export']" data-testid="registration-table-export" @click="exporting = true">
            {{ t('memberTable.export') }}
          </DropdownMenuItem>
        </ActionsMenu>
      </div>
    </div>

    <RegistrationTableExport v-model="exporting" @download="download"/>

    <RecordTable v-if="asTable && drawn" :table="table" plain test-id="confirmed-registrations-table">
      <template #[`cell-${NAME_KEY}`]="{row, text}">
        <MemberName v-if="row.extra?.memberIdentity" :identity="row.extra.memberIdentity">{{ text }}</MemberName>
        <template v-else>{{ text }}</template>
      </template>
      <template v-if="canDecide" #actions="{row}">
        <PrimaryButton
            v-if="row.extra && row.extra.status !== RegistrationStatus.ACCEPTED"
            compact
            :data-testid="`accept-row-${row.extra.id}`"
            @click="emit('accept', row.extra.id)"
        >
          <font-awesome-icon :icon="['fas', 'check']"/>
        </PrimaryButton>
      </template>
      <template #empty>
        <MutedText tag="p" size="sm">{{ t('memberTable.nobody') }}</MutedText>
      </template>
    </RecordTable>
  </div>
</template>
