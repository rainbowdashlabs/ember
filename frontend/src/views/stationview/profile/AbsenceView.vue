/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {StationMember} from '@/api/types'
import {absences, managedMembers as managedMembersApi} from '@/api'
import type {MemberAbsence} from '@/api/absences'
import {useSession} from '@/composables/useSession'
import MemberName from '@/components/avatar/MemberName.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
const {sessionInfo, loaded, isGuardian} = useSession()

const myAbsences = ref<MemberAbsence[]>([])
const managedMembers = ref<StationMember[]>([])
const loading = ref(true)
const error = ref('')
const success = ref('')

// Add absence form
const showAddAbsence = ref(false)
const newAbsenceFrom = ref('')
const newAbsenceUntil = ref('')
const newAbsenceReason = ref('')
const selectedMemberIds = ref<Set<number>>(new Set())
const savingAbsence = ref(false)

const currentMemberId = computed(() => sessionInfo.value?.member?.id ?? 0)

function isAbsenceActive(absence: MemberAbsence): boolean {
  if (!absence.absentFrom || !absence.absentUntil) return false
  const today = new Date().toISOString().slice(0, 10)
  return absence.absentFrom <= today && absence.absentUntil >= today
}

function isAbsenceUpcoming(absence: MemberAbsence): boolean {
  if (!absence.absentFrom) return false
  return absence.absentFrom > new Date().toISOString().slice(0, 10)
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleDateString('de-DE', {day: '2-digit', month: '2-digit', year: 'numeric'})
}

function toggleMember(memberId: number) {
  const s = new Set(selectedMemberIds.value)
  if (s.has(memberId)) s.delete(memberId); else s.add(memberId)
  selectedMemberIds.value = s
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    myAbsences.value = await absences.listMyAbsences()
    if (isGuardian()) {
      const managed = await managedMembersApi.listManaged()
      managedMembers.value = managed.map(m => ({
        id: m.id,
        stationId: m.stationId,
        accountId: m.accountId,
        name: m.name,
        email: m.email
      }))
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function addAbsence() {
  if (!newAbsenceFrom.value || !newAbsenceUntil.value) return
  savingAbsence.value = true
  error.value = ''
  success.value = ''
  try {
    const memberIds = selectedMemberIds.value.size > 0 ? [...selectedMemberIds.value] : undefined
    await absences.createAbsence({
      absentFrom: newAbsenceFrom.value,
      absentUntil: newAbsenceUntil.value,
      reason: newAbsenceReason.value || undefined,
      memberIds,
    })
    myAbsences.value = await absences.listMyAbsences()
    showAddAbsence.value = false
    newAbsenceFrom.value = ''
    newAbsenceUntil.value = ''
    newAbsenceReason.value = ''
    selectedMemberIds.value = new Set()
    success.value = t('profile.absenceCreated')
  } catch {
    error.value = t('common.error')
  } finally {
    savingAbsence.value = false
  }
}

async function removeAbsence(id: number) {
  error.value = ''
  try {
    await absences.deleteAbsence(id)
    myAbsences.value = await absences.listMyAbsences()
  } catch {
    error.value = t('common.error')
  }
}

function openAddForm() {
  showAddAbsence.value = true
  selectedMemberIds.value = new Set([currentMemberId.value])
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
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="!loading">
        <div class="flex items-center justify-between">
          <div/>
          <SecondaryButton :icon="['fas', 'plus']" @click="openAddForm">
            {{ t('profile.absenceAdd') }}
          </SecondaryButton>
        </div>

        <!-- Add absence form -->
        <NeutralContainer v-if="showAddAbsence" class="space-y-4">
          <div class="grid gap-4 sm:grid-cols-3">
            <div class="space-y-1">
              <FieldLabel>{{ t('profile.absenceFrom') }}</FieldLabel>
              <DateInput v-model="newAbsenceFrom"/>
            </div>
            <div class="space-y-1">
              <FieldLabel>{{ t('profile.absenceUntil') }}</FieldLabel>
              <DateInput v-model="newAbsenceUntil"/>
            </div>
            <div class="space-y-1">
              <FieldLabel>{{ t('profile.absenceReason') }}</FieldLabel>
              <TextAreaInput v-model="newAbsenceReason" :placeholder="t('profile.absenceReasonPlaceholder')"/>
            </div>
          </div>

          <!-- Member selection -->
          <div class="space-y-2">
            <FieldLabel>{{ t('profile.absenceFor') }}</FieldLabel>
            <div class="flex flex-wrap gap-2">
              <SelectionToggleButton
                  :selected="selectedMemberIds.has(currentMemberId)"
                  size="md"
                  @toggle="toggleMember(currentMemberId)"
              >
                {{ t('profile.absenceMyself') }}
              </SelectionToggleButton>
              <SelectionToggleButton
                  v-for="m in managedMembers"
                  :key="m.id"
                  :selected="selectedMemberIds.has(m.id)"
                  size="md"
                  @toggle="toggleMember(m.id)"
              >
                {{ m.name ?? m.email }}
              </SelectionToggleButton>
            </div>
          </div>

          <div class="flex gap-3">
            <PrimaryButton
                :disabled="savingAbsence || !newAbsenceFrom || !newAbsenceUntil || selectedMemberIds.size === 0"
                @click="addAbsence">
              {{ savingAbsence ? t('common.loading') : t('profile.absenceAdd') }}
            </PrimaryButton>
            <SecondaryButton @click="showAddAbsence = false">
              {{ t('common.cancel') }}
            </SecondaryButton>
          </div>
        </NeutralContainer>

        <!-- Absence list -->
        <div v-if="myAbsences.length === 0 && !showAddAbsence" class="text-(--text-muted) text-sm text-center py-4">
          {{ t('profile.absenceEmpty') }}
        </div>

        <div class="space-y-2">
          <NeutralContainer
              v-for="absence in myAbsences"
              :key="absence.id"
          >
            <div class="flex items-center justify-between flex-wrap gap-2">
              <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
                <span v-if="absence.memberIdentity"
                      class="text-sm font-semibold"><MemberName :identity="absence.memberIdentity"/></span>
                <span class="text-sm">{{ formatDate(absence.absentFrom) }} – {{
                    formatDate(absence.absentUntil)
                  }}</span>
                <MutedText size="sm" v-if="absence.reason">{{ absence.reason }}</MutedText>
                <span v-if="absence.createdByName" class="text-xs text-(--text-muted) italic">{{ t('common.createdBy', { name: absence.createdByName }) }}</span>
              </div>
              <div class="flex items-center gap-2">
                <SuccessBadge v-if="isAbsenceActive(absence)">{{ t('profile.absenceActive') }}</SuccessBadge>
                <InfoBadge v-else-if="isAbsenceUpcoming(absence)">{{ t('profile.absenceUpcoming') }}</InfoBadge>
                <ErrorBadge v-else>{{ t('profile.absenceExpired') }}</ErrorBadge>
                <DeleteButton @click="removeAbsence(absence.id)"/>
              </div>
            </div>
          </NeutralContainer>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
