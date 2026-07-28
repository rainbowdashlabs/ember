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
import type {MemberGroup, StationMember} from '@/api/types'
import {StationPermission} from '@/api/types'
import {attendance, memberGroups, stationMembers} from '@/api'
import {useSession} from '@/composables/useSession'
import {useSessionMeta} from './sessionview/useSessionMeta'
import {useCheckMode} from './sessionview/useCheckMode'
import {useSessionFields} from './sessionview/useSessionFields'
import SessionContent from './sessionview/SessionContent.vue'
import {saveBlob} from '@/util/downloadAuthed'
import {reportCaughtError} from '@/util/devErrorReporter'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded, hasPermission} = useSession()

const canEdit = computed(() => hasPermission(StationPermission.ATTENDANCE_EDIT))

const sessionId = computed(() => Number(route.params.id))

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

const {setSessionStartTime, setSessionEndTime, setSessionTitle} = useSessionMeta(sessionId, session, error)
const {checkMode, checkIndex, uncheckedEntries, currentCheckEntry, startCheckMode, checkSetStatus, skipCheck} = useCheckMode(entries, setStatus)
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

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [detail, members, allGroups] = await Promise.all([
      attendance.getSession(sessionId.value),
      stationMembers.listMembers(true),
      memberGroups.listGroups(),
    ])
    session.value = detail.session ?? null
    sessionFields.value = detail.fields ?? []
    entries.value = detail.entries ?? []
    allMembers.value = members
    groups.value = allGroups

    if (session.value) {
      await loadTemplateContext(session.value.templateId)
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

async function setCheckIn(entryId: number, time: string) {
  error.value = ''
  try {
    if (time) {
      const [h, m] = time.split(':')
      const d = new Date()
      d.setHours(Number(h), Number(m), 0, 0)
      await attendance.checkIn(entryId, {time: d.toISOString()})
    }
    const detail = await attendance.getSession(sessionId.value)
    entries.value = detail.entries ?? []
  } catch {
    error.value = t('common.error')
  }
}

async function setCheckOut(entryId: number, time: string) {
  error.value = ''
  try {
    if (time) {
      const [h, m] = time.split(':')
      const d = new Date()
      d.setHours(Number(h), Number(m), 0, 0)
      await attendance.checkOut(entryId, {time: d.toISOString()})
    }
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

async function exportPdf() {
  error.value = ''
  try {
    saveBlob(await attendance.exportPdf(sessionId.value), `attendance-${sessionId.value}.pdf`)
  } catch {
    error.value = t('common.error')
  }
}

function goBack() {
  router.push({name: 'attendance-past'})
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
        :check-mode="checkMode"
        :check-index="checkIndex"
        :unchecked-entries="uncheckedEntries"
        :current-check-entry="currentCheckEntry"
        :current-member-name="currentCheckEntry ? getMemberName(currentCheckEntry.memberId) : ''"
        :current-member-identity="currentCheckEntry ? getMemberIdentity(currentCheckEntry.memberId) : null"
        :template-fields="templateFields"
        :field-values="fieldValues"
        :group-members="groupMembers"
        :all-members="allMembers"
        :entries="entries"
        :member-sections="memberSections"
        @back="goBack"
        @export="exportPdf"
        @sync="syncFromEvent"
        @start-check-mode="startCheckMode"
        @update-title="setSessionTitle"
        @update-start-time="setSessionStartTime"
        @update-end-time="setSessionEndTime"
        @check-set-status="checkSetStatus"
        @skip-check="skipCheck"
        @end-check-mode="checkMode = false"
        @field-update="onFieldUpdate"
        @field-member-ids="setFieldMemberIds"
        @set-status="setStatus"
        @check-in="setCheckIn"
        @check-out="setCheckOut"
        @reset-times="resetEntryTimes"
        @add-member="addMember"
    />
  </ViewContent>
</template>
