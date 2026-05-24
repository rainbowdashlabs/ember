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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
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
import CheckModePanel from './sessionview/CheckModePanel.vue'
import SessionFieldsPanel from './sessionview/SessionFieldsPanel.vue'
import MemberEntry from './sessionview/MemberEntry.vue'
import SessionHeader from './sessionview/SessionHeader.vue'

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

// Field editing
const fieldValues = ref<Map<number, string>>(new Map())

// Check mode
const checkMode = ref(false)
const checkIndex = ref(0)

// Add member
const selectedMemberId = ref('')

const uncheckedEntries = computed(() => entries.value.filter(e => e.status === 'UNCONFIRMED'))

const currentCheckEntry = computed(() => {
  if (!checkMode.value || checkIndex.value >= uncheckedEntries.value.length) return null
  return uncheckedEntries.value[checkIndex.value]
})

const membersNotInSession = computed(() => {
  const entryMemberIds = new Set(entries.value.map(e => e.memberId))
  return allMembers.value.filter(m => !entryMemberIds.has(m.id))
})

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

function getEntry(memberId: number): AttendanceEntry | undefined {
  return entries.value.find(e => e.memberId === memberId)
}

function getFieldValue(fieldId: number): string {
  return fieldValues.value.get(fieldId) ?? ''
}

function setFieldValue(fieldId: number, val: string) {
  fieldValues.value = new Map([...fieldValues.value, [fieldId, val]])
}

function parseFieldConfig(configStr?: string): { options?: string[]; groupId?: number; autoAttend?: boolean } {
  if (!configStr) return {}
  try {
    return JSON.parse(configStr)
  } catch {
    return {}
  }
}

async function setFieldMemberIds(fieldId: number, ids: string[]) {
  const val = ids.length === 0 ? '' : ids.length === 1 ? ids[0] : JSON.stringify(ids)
  setFieldValue(fieldId, val)
  await saveField(fieldId)

  const field = templateFields.value.find(f => f.id === fieldId)
  if (field && parseFieldConfig(field.config).autoAttend) {
    const entryMemberIds = new Set(entries.value.map(e => e.memberId))
    for (const id of ids) {
      const mid = Number(id)
      if (!entryMemberIds.has(mid)) {
        entries.value = await attendance.createEntry(sessionId.value, {memberId: mid, source: 'EXTRA'})
        entryMemberIds.add(mid)
      }
      const entry = entries.value.find(e => e.memberId === mid)
      if (entry && entry.status !== 'PRESENT') {
        await attendance.updateEntryStatus(entry.id, 'PRESENT')
      }
    }
    const detail = await attendance.getSession(sessionId.value)
    entries.value = detail.entries ?? []
  }
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [detail, members, allGroups] = await Promise.all([
      attendance.getSession(sessionId.value),
      stationMembers.listMembers(),
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
          if (!entryMemberIds.has(m.id)) {
            entries.value = await attendance.createEntry(sessionId.value, {memberId: m.id, source: 'EXPECTED'})
            entryMemberIds.add(m.id)
          }
        }
      }
    }

    const fv = new Map<number, string>()
    for (const sf of sessionFields.value) {
      let val = sf.value ?? ''
      try {
        val = JSON.parse(val)
      } catch { /* use as-is */ }
      fv.set(sf.fieldId, typeof val === 'string' ? val : String(val))
    }
    fieldValues.value = fv
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function saveField(fieldId: number) {
  try {
    await attendance.setSessionFields(sessionId.value, {
      fields: [{fieldId, value: JSON.stringify(getFieldValue(fieldId))}],
    })
  } catch {
    error.value = t('common.error')
  }
}

const fieldSaveTimers = new Map<number, ReturnType<typeof setTimeout>>()

function onFieldUpdate(fieldId: number, value: string, immediate: boolean) {
  setFieldValue(fieldId, value)
  const existing = fieldSaveTimers.get(fieldId)
  if (existing) clearTimeout(existing)
  if (immediate) {
    saveField(fieldId)
  } else {
    fieldSaveTimers.set(fieldId, setTimeout(() => saveField(fieldId), 500))
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

function startCheckMode() {
  checkIndex.value = 0
  checkMode.value = true
}

async function checkSetStatus(status: AttendanceStatus) {
  if (!currentCheckEntry.value) return
  await setStatus(currentCheckEntry.value.id, status)
  if (checkIndex.value >= uncheckedEntries.value.length) {
    checkMode.value = false
  }
}

function skipCheck() {
  checkIndex.value++
  if (checkIndex.value >= uncheckedEntries.value.length) {
    checkMode.value = false
  }
}

let sessionSaveTimer: ReturnType<typeof setTimeout> | null = null

function saveSessionDebounced() {
  if (sessionSaveTimer) clearTimeout(sessionSaveTimer)
  sessionSaveTimer = setTimeout(saveSessionMeta, 500)
}

async function saveSessionMeta() {
  if (!session.value) return
  error.value = ''
  try {
    const s = session.value
    await attendance.updateSession(sessionId.value, {
      startTime: s.startTime,
      endTime: s.endTime,
      title: s.title,
    })
  } catch {
    error.value = t('common.error')
  }
}

function setSessionStartTime(time: string) {
  if (!session.value || !time) return
  const today = new Date().toISOString().slice(0, 10)
  session.value = {...session.value, startTime: new Date(`${today}T${time}:00`).toISOString()}
  saveSessionMeta()
}

function setSessionEndTime(time: string) {
  if (!session.value || !time) return
  const today = new Date().toISOString().slice(0, 10)
  session.value = {...session.value, endTime: new Date(`${today}T${time}:00`).toISOString()}
  saveSessionMeta()
}

function setSessionTitle(title: string) {
  if (!session.value) return
  session.value = {...session.value, title}
  saveSessionDebounced()
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
      <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
        <SecondaryButton @click="goBack">
          <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-2"/>
          {{ t('attendanceSession.back') }}
        </SecondaryButton>
        <div class="grid grid-cols-2 sm:flex sm:items-center gap-2">
          <SecondaryButton @click="exportPdf">
            <font-awesome-icon :icon="['fas', 'download']" class="mr-1"/>
            {{ t('attendanceSession.export') }}
          </SecondaryButton>
          <SecondaryButton @click="syncFromEvent">
            <font-awesome-icon :icon="['fas', 'clipboard-check']" class="mr-1"/>
            {{ t('attendanceSession.sync') }}
          </SecondaryButton>
          <PrimaryButton v-if="!checkMode && uncheckedEntries.length > 0" class="col-span-2" @click="startCheckMode">
            <font-awesome-icon :icon="['fas', 'clipboard-user']" class="mr-1"/>
            {{ t('attendanceSession.checkMode') }} ({{ uncheckedEntries.length }})
          </PrimaryButton>
        </div>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && session">
        <SessionHeader
            :session="session"
            @update-title="setSessionTitle"
            @update-start-time="setSessionStartTime"
            @update-end-time="setSessionEndTime"
        />

        <!-- Check mode -->
        <CheckModePanel
            v-if="checkMode"
            :current-entry="currentCheckEntry"
            :check-index="checkIndex"
            :total-unchecked="uncheckedEntries.length"
            :member-name="currentCheckEntry ? getMemberName(currentCheckEntry.memberId) : ''"
            @set-status="checkSetStatus"
            @skip="skipCheck"
            @end="checkMode = false"
        />

        <template v-if="!checkMode">
          <!-- Template fields -->
          <SessionFieldsPanel
              :template-fields="templateFields"
              :field-values="fieldValues"
              :group-members="groupMembers"
              :all-members="allMembers"
              @field-update="onFieldUpdate"
              @field-member-ids="setFieldMemberIds"
          />

          <!-- Summary -->
          <div class="flex gap-3 text-sm flex-wrap">
            <SecondaryBadge v-if="entries.filter(e => e.status === 'UNCONFIRMED').length > 0">
              {{ entries.filter(e => e.status === 'UNCONFIRMED').length }} {{ t('attendanceSession.unconfirmed') }}
            </SecondaryBadge>
            <SuccessBadge>{{ entries.filter(e => e.status === 'PRESENT').length }} {{ t('attendanceSession.present') }}</SuccessBadge>
            <ErrorBadge>{{ entries.filter(e => e.status === 'ABSENT').length }} {{ t('attendanceSession.absent') }}</ErrorBadge>
            <InfoBadge>{{ entries.filter(e => e.status === 'DECLINED').length }} {{ t('attendanceSession.declined') }}</InfoBadge>
          </div>

          <!-- Members by group -->
          <div v-for="section in memberSections" :key="section.group?.id ?? 'ungrouped'" class="space-y-2">
            <SubHeader>{{ section.group?.name ?? t('attendanceSession.otherMembers') }}</SubHeader>
            <div class="space-y-1">
              <MemberEntry
                  v-for="member in section.members"
                  :key="member.id"
                  :member="member"
                  :entry="getEntry(member.id)"
                  :member-name="getMemberName(member.id)"
                  @set-status="setStatus"
                  @check-in="setCheckIn"
                  @check-out="setCheckOut"
                  @reset-times="resetEntryTimes"
              />
            </div>
          </div>

          <!-- Add member -->
          <div v-if="membersNotInSession.length > 0" class="flex items-center gap-2">
            <SelectInput v-model="selectedMemberId" class="flex-1">
              <option disabled value="">{{ t('attendanceSession.addMember') }}</option>
              <option v-for="m in membersNotInSession" :key="m.id" :value="String(m.id)">
                {{ m.name ?? m.email }}
              </option>
            </SelectInput>
            <PrimaryButton :disabled="!selectedMemberId" @click="addMember">
              <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
              {{ t('attendanceSession.add') }}
            </PrimaryButton>
          </div>
        </template>
      </template>
    </div>
  </ViewContent>
</template>
