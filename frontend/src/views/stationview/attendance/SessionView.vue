/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import type {
  AttendanceEntry,
  AttendanceSession,
  AttendanceSessionField,
  AttendanceStatus,
  AttendanceTemplateField,
  TemplateGroupEntry,
} from '@/api/attendance'
import {StationPermission, type MemberGroup, type StationMember} from '@/api/types'
import {attendance, events, memberGroups, stationMembers} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncAction} from '@/composables/useAsyncAction'
import type {SheetOptions} from '@/api/attendance'
import ExportSheetModal from './sessionview/ExportSheetModal.vue'
import {useSessionMeta} from './sessionview/useSessionMeta'
import {useCheckMode, type CheckRow} from './sessionview/useCheckMode'
import {useSessionFields} from './sessionview/useSessionFields'
import {useSessionNotes} from './sessionview/useSessionNotes'
import SessionContent from './sessionview/SessionContent.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {presentDocument} from '@/util/documentView'
import {localInputToInstant, timeOnDayOf} from '@/util/format'
import {reportCaughtError} from '@/util/devErrorReporter'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded, hasPermission, sessionInfo} = useSession()

const canManage = computed(() => hasPermission(StationPermission.ATTENDANCE_MANAGER))

/**
 * Whether the sheet is closed. Decided by the backend, which owns the span and the two moments that
 * can override it, so the rule is not written down a second time here where it could drift.
 */
const locked = ref(false)

const canEdit = computed(() => hasPermission(StationPermission.ATTENDANCE_EDIT) && !locked.value)

/**
 * Seeing that a swap is waiting and being allowed to move it on are different rights, and so are
 * seeing a found item and signing it over. The server already leaves out what may not be seen; these
 * decide whether the button beside it is offered.
 */
const canManageSwap = computed(() => hasPermission(StationPermission.INVENTORY_MOVEMENTS))
const canSignOffFound = computed(() => hasPermission(StationPermission.LOST_AND_FOUND_MANAGE))

const sessionId = computed(() => Number(route.params.id))

const showDeleteConfirm = ref(false)
const session = ref<AttendanceSession | null>(null)
const sessionFields = ref<AttendanceSessionField[]>([])
const templateFields = ref<AttendanceTemplateField[]>([])
const templateGroups = ref<TemplateGroupEntry[]>([])
const entries = ref<AttendanceEntry[]>([])
const allMembers = ref<StationMember[]>([])
const groups = ref<MemberGroup[]>([])
const groupMembers = ref<Map<number, StationMember[]>>(new Map())
const loading = ref(true)
const error = ref('')

const selectedMemberId = ref('')

const eventStartTime = ref<string | null>(null)
const eventEndTime = ref<string | null>(null)

/** Whether the sheet runs into another day, which is when every time it shows needs its date. */
const spansDays = computed(() => {
  const start = session.value?.startTime
  const end = session.value?.endTime
  if (!start || !end) return false
  return new Date(start).toDateString() !== new Date(end).toDateString()
})

const {
  setSessionStartTime,
  setSessionEndTime,
  setSessionTitle,
  setCountedHours,
  takeEventTimes,
} = useSessionMeta(sessionId, session, error)
/**
 * Every name on the sheet that is still open, in the order the sheet reads.
 *
 * <p>A member the template expects who has no entry yet belongs here as much as one whose entry says
 * nothing: both are names nobody has decided about, and leaving the first kind out is what emptied
 * the walk and took its button off the screen.
 */
const openRows = computed((): CheckRow[] => {
  const rows: CheckRow[] = []
  for (const section of memberSections.value) {
    for (const member of section.members) {
      const entry = entries.value.find(e => e.memberId === member.id)
      if (!entry) rows.push({memberId: member.id, entryId: null})
      else if (entry.status === 'UNCONFIRMED') rows.push({memberId: member.id, entryId: entry.id})
    }
  }
  return rows
})

const {checkMode, checkIndex, currentCheckRow, startCheckMode, checkSetStatus, skipCheck} = useCheckMode(openRows, markRow)
const {fieldValues, parseFieldConfig, onFieldUpdate, setFieldMemberIds, initFieldValues} = useSessionFields(sessionId, templateFields, entries, error)

interface MemberSection {
  group: MemberGroup | null
  members: StationMember[]
}

const memberSections = computed((): MemberSection[] => {
  const sections: MemberSection[] = []
  const assignedMemberIds = new Set<number>()
  const sortByName = (a: StationMember, b: StationMember) =>
      (a.name ?? '').localeCompare(b.name ?? '', 'de')

  for (const tg of templateGroups.value) {
    const group = groups.value.find(g => g.id === tg.groupId)
    if (!group) continue
    const members = [...(groupMembers.value.get(tg.groupId) ?? [])].sort(sortByName)
    if (members.length > 0) {
      sections.push({group, members})
      members.forEach(m => assignedMemberIds.add(m.id))
    }
  }

  const ungroupedMembers = entries.value
      .filter(e => !assignedMemberIds.has(e.memberId))
      .map(e => allMembers.value.find(m => m.id === e.memberId))
      .filter((m): m is StationMember => m != null)
      .sort(sortByName)

  if (ungroupedMembers.length > 0) {
    sections.push({group: null, members: ungroupedMembers})
  }
  return sections
})

function getMemberName(memberId: number): string {
  const m = allMembers.value.find(mm => mm.id === memberId)
  return m?.name ?? m?.email ?? `#${memberId}`
}

function getMemberIdentity(memberId: number) {
  return allMembers.value.find(mm => mm.id === memberId)?.identity ?? null
}

function referencedGroupIds(fields: AttendanceTemplateField[]): Set<number> {
  const groupIds = new Set<number>()
  for (const tg of templateGroups.value) groupIds.add(tg.groupId)
  for (const field of fields) {
    const cfg = parseFieldConfig(field.config)
    if (cfg.groupId) groupIds.add(cfg.groupId)
  }
  return groupIds
}

async function loadGroupMembers(fields: AttendanceTemplateField[]): Promise<Map<number, StationMember[]>> {
  const byGroup = new Map<number, StationMember[]>()
  for (const groupId of referencedGroupIds(fields)) {
    try {
      byGroup.set(groupId, await memberGroups.getGroupMembers(groupId))
    } catch (e) {
      reportCaughtError(e, 'attendance group member listing')
    }
  }
  return byGroup
}

async function loadTemplateContext(templateId: number) {
  const [tplFields, tplDetail] = await Promise.all([
    attendance.listTemplateFields(templateId),
    attendance.getTemplate(templateId),
  ])
  templateFields.value = tplFields
  templateGroups.value = tplDetail.groups ?? []
  groupMembers.value = await loadGroupMembers(tplFields)
}

/**
 * When the appointment behind the sheet runs, so the sheet can offer its times back.
 *
 * <p>Only for the offer: the sheet's own times are what count from the moment it was made, and an
 * appointment that moves afterwards leaves the sheet where it is.
 */
async function loadEventTimes(eventId: number | null) {
  eventStartTime.value = null
  eventEndTime.value = null
  if (!eventId) return
  try {
    const event = await events.getEvent(eventId)
    eventStartTime.value = event.startTime ?? null
    eventEndTime.value = event.endTime ?? null
  } catch (e) {
    reportCaughtError(e, 'attendance sheet appointment times')
  }
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [detail, members, allGroups] = await Promise.all([
      attendance.getSession(sessionId.value),
      stationMembers.listMembers(true),
      memberGroups.listGroups(),
    ])
    await loadNotes()
    session.value = detail.session ?? null
    sessionFields.value = detail.fields ?? []
    entries.value = detail.entries ?? []
    locked.value = detail.locked ?? false
    allMembers.value = members
    groups.value = allGroups

    if (session.value) {
      await loadTemplateContext(session.value.templateId)
      await loadEventTimes(session.value.eventId ?? null)
    }

    initFieldValues(sessionFields.value)
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function addMember() {
  if (!selectedMemberId.value) return
  error.value = ''
  try {
    entries.value = await attendance.createEntry(sessionId.value, {memberId: Number(selectedMemberId.value)})
    selectedMemberId.value = ''
  } catch {
    error.value = t('common.error')
  }
}

/**
 * Marks one name of the walk, writing its entry first where the sheet has none.
 *
 * <p>Somebody expected but never entered has nothing to mark against, so the mark itself is what puts
 * them on the sheet. Nothing is written for a name that is only passed over.
 */
async function markRow(row: CheckRow, status: AttendanceStatus) {
  if (row.entryId != null) {
    await setStatus(row.entryId, status)
    return
  }
  error.value = ''
  try {
    entries.value = await attendance.createEntry(sessionId.value, {memberId: row.memberId})
    const created = entries.value.find(e => e.memberId === row.memberId)
    if (created) await setStatus(created.id, status)
  } catch {
    error.value = t('common.error')
  }
}

async function setStatus(entryId: number, status: AttendanceStatus) {
  error.value = ''
  try {
    await attendance.updateEntryStatus(entryId, status)
    const detail = await attendance.getSession(sessionId.value)
    entries.value = detail.entries ?? []
  } catch {
    error.value = t('common.error')
  }
}

/**
 * The moment a member's field stands for.
 *
 * <p>A sheet over several days is written whole, day and time together. A sheet over one day is
 * written as a time alone, and that time belongs to the day the sheet runs on: reading it onto today
 * is what once moved an arrival written on an older sheet forward by however long ago it was, and
 * every hour counted from it with it.
 */
function momentOnSheet(value: string): string {
  if (!value) return ''
  return value.includes('T') ? localInputToInstant(value) : timeOnDayOf(session.value?.startTime, value)
}

async function setCheckIn(entryId: number, time: string) {
  error.value = ''
  try {
    const moment = momentOnSheet(time)
    if (moment) await attendance.checkIn(entryId, {time: moment})
    const detail = await attendance.getSession(sessionId.value)
    entries.value = detail.entries ?? []
  } catch {
    error.value = t('common.error')
  }
}

async function setCheckOut(entryId: number, time: string) {
  error.value = ''
  try {
    const moment = momentOnSheet(time)
    if (moment) await attendance.checkOut(entryId, {time: moment})
    const detail = await attendance.getSession(sessionId.value)
    entries.value = detail.entries ?? []
  } catch {
    error.value = t('common.error')
  }
}

async function resetEntryTimes(entryId: number) {
  error.value = ''
  try {
    await attendance.resetTimes(entryId)
    const detail = await attendance.getSession(sessionId.value)
    entries.value = detail.entries ?? []
  } catch {
    error.value = t('common.error')
  }
}

async function syncFromEvent() {
  error.value = ''
  try {
    entries.value = await attendance.syncFromEvent(sessionId.value)
  } catch {
    error.value = t('common.error')
  }
}

const {memberNotes, loadNotes, moveSwap, dropSwap, signOffFound} = useSessionNotes(sessionId, error)

async function unlockSession() {
  error.value = ''
  try {
    await attendance.unlockSession(sessionId.value)
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

async function lockSession() {
  error.value = ''
  try {
    await attendance.lockSession(sessionId.value)
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

const showExportOptions = ref(false)

const {running: exporting, run: exportSheet} = useAsyncAction(async (options: SheetOptions) => {
  error.value = ''
  try {
    presentDocument(await attendance.exportPdf(sessionId.value, options))
    showExportOptions.value = false
  } catch {
    error.value = t('common.error')
  }
})

function goBack() {
  router.push({name: 'attendance-past'})
}

/**
 * Throws the whole attendance away, once it has been asked about.
 *
 * <p>Everything taken on it goes with it, which is why it is asked about: a sheet opened for the
 * wrong evening is undone here, and one filled in for the right one is not.
 */
async function removeSession() {
  error.value = ''
  try {
    await attendance.deleteSession(sessionId.value)
    showDeleteConfirm.value = false
    router.push({name: 'attendance-past'})
  } catch {
    error.value = t('common.error')
  }
}

onMounted(() => {
  if (loaded.value) loadData()
})

watch(loaded, (isLoaded) => {
  if (isLoaded && loading.value) loadData()
})
</script>

<template>
  <ViewContent
      :title="t('pages.attendance-session.title')"
      :subtitle="t('pages.attendance-session.subtitle')"
  >
    <SessionContent
        v-model:selected-member-id="selectedMemberId"
        :loading="loading"
        :error="error"
        :session="session"
        :can-edit="canEdit"
        :locked="locked"
        :can-manage="canManage"
        :member-notes="memberNotes"
        :can-manage-swap="canManageSwap"
        :can-sign-off-found="canSignOffFound"
        :check-mode="checkMode"
        :check-index="checkIndex"
        :open-rows="openRows"
        :current-check-row="currentCheckRow"
        :current-member-name="currentCheckRow ? getMemberName(currentCheckRow.memberId) : ''"
        :current-member-identity="currentCheckRow ? getMemberIdentity(currentCheckRow.memberId) : null"
        :template-fields="templateFields"
        :field-values="fieldValues"
        :group-members="groupMembers"
        :all-members="allMembers"
        :entries="entries"
        :member-sections="memberSections"
        :event-start-time="eventStartTime"
        :event-end-time="eventEndTime"
        :spans-days="spansDays"
        @back="goBack"
        @export="showExportOptions = true"
        @sync="syncFromEvent"
        @start-check-mode="startCheckMode"
        @remove="showDeleteConfirm = true"
        @update-title="setSessionTitle"
        @update-start-time="setSessionStartTime"
        @update-end-time="setSessionEndTime"
        @update-counted-hours="setCountedHours"
        @take-event-times="takeEventTimes"
        @check-set-status="checkSetStatus"
        @skip-check="skipCheck"
        @end-check-mode="checkMode = false"
        @field-update="onFieldUpdate"
        @field-member-ids="setFieldMemberIds"
        @set-status="setStatus"
        @enter="(memberId, status) => markRow({memberId, entryId: null}, status)"
        @unlock="unlockSession"
        @lock="lockSession"
        @move-swap="moveSwap"
        @drop-swap="dropSwap"
        @sign-off-found="signOffFound"
        @check-in="setCheckIn"
        @check-out="setCheckOut"
        @reset-times="resetEntryTimes"
        @add-member="addMember"
    />

    <ExportSheetModal
        v-model="showExportOptions"
        :exporting="exporting"
        :session-title="session?.title ?? ''"
        :shows-instance-url="!sessionInfo?.pdfHidesInstanceUrl"
        @export="exportSheet"
    />

    <ConfirmDeleteModal
        v-model="showDeleteConfirm"
        :message="t('attendanceSession.deleteConfirm')"
        @confirm="removeSession"
    />
  </ViewContent>
</template>
