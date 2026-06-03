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
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {
  AttendanceEntry,
  AttendanceSession,
  AttendanceSessionField,
  AttendanceStatus,
  AttendanceTemplateField,
  MemberGroup,
  StationMember,
  TemplateGroupEntry
} from '@/api/types'
import {attendance, memberGroups, stationMembers} from '@/api'
import {useSession} from '@/composables/useSession'
import {useSessionMeta} from './sessionview/useSessionMeta'
import {useCheckMode} from './sessionview/useCheckMode'
import {useSessionFields} from './sessionview/useSessionFields'
import SessionToolbar from './sessionview/SessionToolbar.vue'
import SessionHeader from './sessionview/SessionHeader.vue'
import CheckModePanel from './sessionview/CheckModePanel.vue'
import SessionFieldsPanel from './sessionview/SessionFieldsPanel.vue'
import AttendanceSummary from './sessionview/AttendanceSummary.vue'
import MemberListPanel from './sessionview/MemberListPanel.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded} = useSession()

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
      const [tplFields, tplDetail] = await Promise.all([
        attendance.listTemplateFields(session.value.templateId),
        attendance.getTemplate(session.value.templateId),
      ])
      templateFields.value = tplFields
      templateGroups.value = tplDetail.groups ?? []

      const groupIdsToLoad = new Set<number>()
      for (const tg of templateGroups.value) groupIdsToLoad.add(tg.groupId)
      for (const field of tplFields) {
        const cfg = parseFieldConfig(field.config)
        if (cfg.groupId) groupIdsToLoad.add(cfg.groupId)
      }

      const gm = new Map<number, StationMember[]>()
      for (const groupId of groupIdsToLoad) {
        try {
          const members = await memberGroups.getGroupMembers(groupId)
          gm.set(groupId, members)
        } catch { /* skip */ }
      }
      groupMembers.value = gm

      const entryMemberIds = new Set(entries.value.map(e => e.memberId))
      for (const tg of templateGroups.value) {
        const members = gm.get(tg.groupId) ?? []
        for (const m of members) {
          if (!entryMemberIds.has(m.id) && !m.formerAt) {
            entries.value = await attendance.createEntry(sessionId.value, {memberId: m.id, source: 'EXPECTED'})
            entryMemberIds.add(m.id)
          }
        }
      }
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
    const blob = await attendance.exportPdf(sessionId.value)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `attendance-${sessionId.value}.pdf`
    a.click()
    URL.revokeObjectURL(url)
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
  <ViewContent>
    <div class="space-y-6">
      <SessionToolbar
          :check-mode="checkMode"
          :unchecked-count="uncheckedEntries.length"
          @back="goBack"
          @export="exportPdf"
          @sync="syncFromEvent"
          @start-check-mode="startCheckMode"
      />

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && session">
        <SessionHeader
            :session="session"
            @update-title="setSessionTitle"
            @update-start-time="setSessionStartTime"
            @update-end-time="setSessionEndTime"
        />

        <CheckModePanel
            v-if="checkMode"
            :current-entry="currentCheckEntry"
            :check-index="checkIndex"
            :total-unchecked="uncheckedEntries.length"
            :member-name="currentCheckEntry ? getMemberName(currentCheckEntry.memberId) : ''"
            :member-identity="currentCheckEntry ? getMemberIdentity(currentCheckEntry.memberId) : null"
            @set-status="checkSetStatus"
            @skip="skipCheck"
            @end="checkMode = false"
        />

        <template v-if="!checkMode">
          <SessionFieldsPanel
              :template-fields="templateFields"
              :field-values="fieldValues"
              :group-members="groupMembers"
              :all-members="allMembers"
              @field-update="onFieldUpdate"
              @field-member-ids="setFieldMemberIds"
          />

          <AttendanceSummary :entries="entries" :member-sections="memberSections"/>

          <MemberListPanel
              :entries="entries"
              :all-members="allMembers"
              :member-sections="memberSections"
              :selected-member-id="selectedMemberId"
              @set-status="setStatus"
              @check-in="setCheckIn"
              @check-out="setCheckOut"
              @reset-times="resetEntryTimes"
              @add-member="addMember"
              @update:selected-member-id="selectedMemberId = $event"
          />
        </template>
      </template>
    </div>
  </ViewContent>
</template>
