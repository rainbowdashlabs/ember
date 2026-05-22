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
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import InfoButton from '@/components/button/InfoButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TimeShortInput from '@/components/input/datetime/TimeShortInput.vue'
import ProfileFieldInput from '@/components/input/ProfileFieldInput.vue'
import MultiSelectInput from '@/components/input/select/MultiSelectInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
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
import {useStations} from '@/composables/useStations'
import {useBreakpoint} from '@/composables/useBreakpoint'
import MemberName from '@/components/avatar/MemberName.vue'

const {t} = useI18n()
const {isMobile} = useBreakpoint()
const route = useRoute()
const router = useRouter()
const {loaded} = useSession()
const {currentStationId} = useStations()

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

// Group members into sections based on template groups
interface MemberSection {
  group: MemberGroup | null
  members: StationMember[]
}

const memberSections = computed((): MemberSection[] => {
  const sections: MemberSection[] = []
  const assignedMemberIds = new Set<number>()

  const sortByName = (a: StationMember, b: StationMember) =>
      (a.name ?? '').localeCompare(b.name ?? '', 'de')

  // Template-defined groups in order
  for (const tg of templateGroups.value) {
    const group = groups.value.find(g => g.id === tg.groupId)
    if (!group) continue
    const members = [...(groupMembers.value.get(tg.groupId) ?? [])].sort(sortByName)
    if (members.length > 0) {
      sections.push({group, members})
      members.forEach(m => assignedMemberIds.add(m.id))
    }
  }

  // Members in entries but not in any group
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

function formatTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
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

function isMemberField(fieldType: string): boolean {
  return ['member', 'member_list', 'member_of_group', 'member_list_of_group'].includes(fieldType)
}

function isListField(fieldType: string): boolean {
  return ['member_list', 'member_list_of_group'].includes(fieldType)
}

function getMemberOptions(field: AttendanceTemplateField): { value: string; label: string }[] {
  const config = parseFieldConfig(field.config)
  const groupId = config.groupId
  let members: StationMember[]
  if (groupId && groupMembers.value.has(groupId)) {
    members = groupMembers.value.get(groupId)!
  } else {
    members = allMembers.value
  }
  return members.map(m => ({value: String(m.id), label: m.name ?? m.email ?? `#${m.id}`}))
}

function getFieldMemberIds(fieldId: number): string[] {
  const raw = getFieldValue(fieldId)
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed)) return parsed.map(String)
    if (parsed) return [String(parsed)]
  } catch { /* ignore */
  }
  if (raw) return [raw]
  return []
}

async function setFieldMemberIds(fieldId: number, ids: string[]) {
  const val = ids.length === 0 ? '' : ids.length === 1 ? ids[0] : JSON.stringify(ids)
  setFieldValue(fieldId, val)
  await saveField(fieldId)

  // Auto-create attendance entries for autoAttend fields and mark as PRESENT
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
    // Refresh entries to get updated statuses
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
      stationMembers.listMembers(currentStationId.value!, true),
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

      // Collect all group IDs needed: template groups + field config groups
      const groupIdsToLoad = new Set<number>()
      for (const tg of templateGroups.value) {
        groupIdsToLoad.add(tg.groupId)
      }
      for (const field of tplFields) {
        const cfg = parseFieldConfig(field.config)
        if (cfg.groupId) groupIdsToLoad.add(cfg.groupId)
      }

      // Load group members
      const gm = new Map<number, StationMember[]>()
      for (const groupId of groupIdsToLoad) {
        try {
          const members = await memberGroups.getGroupMembers(groupId)
          gm.set(groupId, members)
        } catch { /* skip */
        }
      }
      groupMembers.value = gm

      // Auto-create entries for template group members not yet in session
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

    // Populate field values from session fields
    const fv = new Map<number, string>()
    for (const sf of sessionFields.value) {
      let val = sf.value ?? ''
      try {
        val = JSON.parse(val)
      } catch { /* use as-is */
      }
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

function onFieldUpdate(fieldId: number, value: string, immediate = false) {
  setFieldValue(fieldId, value)
  // Clear pending timer
  const existing = fieldSaveTimers.get(fieldId)
  if (existing) clearTimeout(existing)
  if (immediate) {
    saveField(fieldId)
  } else {
    // Debounce text fields
    fieldSaveTimers.set(fieldId, setTimeout(() => saveField(fieldId), 500))
  }
}

function isImmediateField(fieldType: string): boolean {
  return ['boolean', 'date', 'enum', 'member', 'member_list', 'member_of_group', 'member_list_of_group'].includes(fieldType)
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
      // Convert HH:mm to today's ISO timestamp
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

function endCheckMode() {
  checkMode.value = false
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
        <!-- Session header (title + times when no event linked) -->
        <NeutralContainer v-if="!session.eventId" class="space-y-3">
          <div class="grid gap-3 sm:grid-cols-3">
            <div class="space-y-1">
              <label class="block text-sm font-medium">{{ t('attendanceSession.title') }}</label>
              <TextInput :model-value="session.title ?? ''" @update:model-value="setSessionTitle(($event as string) ?? '')"/>
            </div>
            <div class="space-y-1">
              <label class="block text-sm font-medium">{{ t('attendanceSession.startTime') }}</label>
              <TimeShortInput
                  :model-value="formatTime(session.startTime)"
                  @change="setSessionStartTime(($event.target as HTMLInputElement).value)"
              />
            </div>
            <div class="space-y-1">
              <label class="block text-sm font-medium">{{ t('attendanceSession.endTime') }}</label>
              <TimeShortInput
                  :model-value="formatTime(session.endTime)"
                  @change="setSessionEndTime(($event.target as HTMLInputElement).value)"
              />
            </div>
          </div>
        </NeutralContainer>

        <!-- Event-linked session title -->
        <SectionHeader v-if="session.eventId && session.title">{{ session.title }}</SectionHeader>

        <!-- Check mode -->
        <NeutralContainer v-if="checkMode && currentCheckEntry" class="space-y-4">
          <SectionHeader>{{ t('attendanceSession.checkMode') }}</SectionHeader>
          <div class="text-center space-y-4 py-4">
            <p class="text-2xl font-bold"><MemberName :name="getMemberName(currentCheckEntry.memberId)" :member-id="currentCheckEntry!.memberId" size="md"/></p>
            <p class="text-sm text-(--text-muted)">{{ checkIndex + 1 }} / {{ uncheckedEntries.length }}</p>
            <div class="flex flex-col sm:flex-row justify-center gap-3 sm:gap-4">
              <SuccessButton :full-width="isMobile" @click="checkSetStatus('PRESENT')">
                <font-awesome-icon :icon="['fas', 'check']" class="mr-2"/>
                {{ t('attendanceSession.present') }}
              </SuccessButton>
              <ErrorButton :full-width="isMobile" @click="checkSetStatus('ABSENT')">
                <font-awesome-icon :icon="['fas', 'xmark']" class="mr-2"/>
                {{ t('attendanceSession.absent') }}
              </ErrorButton>
              <InfoButton :full-width="isMobile" @click="checkSetStatus('DECLINED')">
                <font-awesome-icon :icon="['fas', 'ban']" class="mr-2"/>
                {{ t('attendanceSession.declined') }}
              </InfoButton>
            </div>
            <div class="flex justify-center gap-3 pt-2">
              <SecondaryButton @click="skipCheck">{{ t('attendanceSession.skip') }}</SecondaryButton>
              <SecondaryButton @click="endCheckMode">{{
                  t('attendanceSession.endCheck')
                }}
              </SecondaryButton>
            </div>
          </div>
        </NeutralContainer>

        <div v-if="checkMode && !currentCheckEntry" class="text-center py-6">
          <p class="text-lg font-semibold text-success">{{ t('attendanceSession.allChecked') }}</p>
          <SecondaryButton class="mt-3" @click="endCheckMode">{{ t('attendanceSession.endCheck') }}</SecondaryButton>
        </div>

        <template v-if="!checkMode">
          <!-- Template fields -->
          <NeutralContainer v-if="templateFields.length > 0" class="space-y-4">
            <SectionHeader>{{ t('attendanceSession.fields') }}</SectionHeader>
            <div class="space-y-3">
              <div v-for="field in templateFields" :key="field.id" class="space-y-1">
                <label class="block text-sm font-medium">{{ field.name }}</label>
                <!-- Member list fields -->
                <template v-if="isMemberField(field.fieldType ?? '')">
                  <MultiSelectInput
                      v-if="isListField(field.fieldType ?? '')"
                      :model-value="getFieldMemberIds(field.id)"
                      :options="getMemberOptions(field)"
                      :placeholder="t('attendanceSession.addMember')"
                      @update:model-value="setFieldMemberIds(field.id, $event)"
                  />
                  <SelectInput
                      v-else
                      :model-value="getFieldValue(field.id)"
                      @update:model-value="setFieldMemberIds(field.id, $event ? [$event] : [])"
                  >
                    <option value="">—</option>
                    <option v-for="opt in getMemberOptions(field)" :key="opt.value" :value="opt.value">{{
                        opt.label
                      }}
                    </option>
                  </SelectInput>
                </template>
                <!-- Regular fields -->
                <template v-else>
                  <ProfileFieldInput
                      :field-type="field.fieldType ?? 'text'"
                      :model-value="getFieldValue(field.id)"
                      :options="(parseFieldConfig(field.config).options as string[]) ?? []"
                      @update:model-value="onFieldUpdate(field.id, $event, isImmediateField(field.fieldType ?? ''))"
                  />
                </template>
              </div>
            </div>
          </NeutralContainer>

          <!-- Summary -->
          <div class="flex gap-3 text-sm flex-wrap">
            <SecondaryBadge v-if="entries.filter(e => e.status === 'UNCONFIRMED').length > 0">
              {{ entries.filter(e => e.status === 'UNCONFIRMED').length }} {{ t('attendanceSession.unconfirmed') }}
            </SecondaryBadge>
            <SuccessBadge>{{ entries.filter(e => e.status === 'PRESENT').length }} {{
                t('attendanceSession.present')
              }}
            </SuccessBadge>
            <ErrorBadge>{{ entries.filter(e => e.status === 'ABSENT').length }} {{
                t('attendanceSession.absent')
              }}
            </ErrorBadge>
            <InfoBadge>{{ entries.filter(e => e.status === 'DECLINED').length }} {{
                t('attendanceSession.declined')
              }}
            </InfoBadge>
          </div>

          <!-- Members by group -->
          <div v-for="section in memberSections" :key="section.group?.id ?? 'ungrouped'" class="space-y-2">
            <SubHeader>{{ section.group?.name ?? t('attendanceSession.otherMembers') }}</SubHeader>
            <div class="space-y-1">
              <div
                  v-for="member in section.members"
                  :key="member.id"
                  :class="{
                  'border-success bg-success/5': getEntry(member.id)?.status === 'PRESENT',
                  'border-error bg-error/5': getEntry(member.id)?.status === 'ABSENT',
                  'border-info bg-info/5': getEntry(member.id)?.status === 'DECLINED',
                  'border-bg-light-accent dark:border-bg-dark-accent bg-bg-light-accent/20 dark:bg-bg-dark-accent/20': !getEntry(member.id) || getEntry(member.id)?.status === 'UNCONFIRMED',
                }"
                  class="rounded-lg px-4 py-3 border-l-4 transition-all"
              >
                <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
                  <div class="flex items-center gap-3 min-w-0">
                    <font-awesome-icon
                        v-if="getEntry(member.id)?.status === 'PRESENT'"
                        :icon="['fas', 'check']" class="h-4 w-4 text-success shrink-0"
                    />
                    <font-awesome-icon
                        v-else-if="getEntry(member.id)?.status === 'ABSENT'"
                        :icon="['fas', 'xmark']" class="h-4 w-4 text-error shrink-0"
                    />
                    <font-awesome-icon
                        v-else-if="getEntry(member.id)?.status === 'DECLINED'"
                        :icon="['fas', 'ban']" class="h-4 w-4 text-info shrink-0"
                    />
                    <font-awesome-icon
                        v-else
                        :icon="['fas', 'asterisk']" class="h-4 w-4 text-(--text-muted) shrink-0"
                    />
                    <MemberName :name="getMemberName(member.id)" :member-id="member.id" class="font-medium text-sm truncate"/>
                  </div>
                  <div v-if="getEntry(member.id)" class="flex flex-col sm:flex-row sm:items-center gap-2">
                    <!-- Status buttons -->
                    <div class="flex items-center gap-2 w-full sm:w-auto">
                      <SuccessButton
                          :class="{ 'opacity-40': getEntry(member.id)!.status === 'PRESENT' }"
                          :disabled="getEntry(member.id)!.status === 'PRESENT'"
                          :full-width="isMobile"
                          class="text-xs flex-1 sm:flex-initial"
                          @click="setStatus(getEntry(member.id)!.id, 'PRESENT')"
                      >
                        <font-awesome-icon :icon="['fas', 'check']"/>
                      </SuccessButton>
                      <ErrorButton
                          :class="{ 'opacity-40': getEntry(member.id)!.status === 'ABSENT' }"
                          :disabled="getEntry(member.id)!.status === 'ABSENT'"
                          :full-width="isMobile"
                          class="text-xs flex-1 sm:flex-initial"
                          @click="setStatus(getEntry(member.id)!.id, 'ABSENT')"
                      >
                        <font-awesome-icon :icon="['fas', 'xmark']"/>
                      </ErrorButton>
                      <InfoButton
                          :class="{ 'opacity-40': getEntry(member.id)!.status === 'DECLINED' }"
                          :disabled="getEntry(member.id)!.status === 'DECLINED'"
                          :full-width="isMobile"
                          class="text-xs flex-1 sm:flex-initial"
                          @click="setStatus(getEntry(member.id)!.id, 'DECLINED')"
                      >
                        <font-awesome-icon :icon="['fas', 'ban']"/>
                      </InfoButton>
                    </div>
                    <!-- Time inputs for PRESENT -->
                    <div v-if="getEntry(member.id)!.status === 'PRESENT'" class="flex items-center gap-1 text-xs">
                      <TimeShortInput
                          :model-value="formatTime(getEntry(member.id)?.checkIn)"
                          class="w-20 text-xs"
                          @change="setCheckIn(getEntry(member.id)!.id, ($event.target as HTMLInputElement).value)"
                      />
                      <span class="text-(--text-muted)">–</span>
                      <TimeShortInput
                          :model-value="formatTime(getEntry(member.id)?.checkOut)"
                          class="w-20 text-xs"
                          @change="setCheckOut(getEntry(member.id)!.id, ($event.target as HTMLInputElement).value)"
                      />
                      <button
                          v-if="getEntry(member.id)?.checkIn || getEntry(member.id)?.checkOut"
                          :title="t('attendanceSession.resetTimes')"
                          class="text-(--text-muted) hover:text-error cursor-pointer"
                          @click="resetEntryTimes(getEntry(member.id)!.id)"
                      >
                        <font-awesome-icon :icon="['fas', 'xmark']" class="h-3 w-3"/>
                      </button>
                    </div>
                  </div>
                  <span v-else class="text-xs text-(--text-muted)">{{ t('attendanceSession.noEntry') }}</span>
                </div>
              </div>
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
