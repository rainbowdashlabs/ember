/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import type { MemberAbsence } from '@/api/absences'
import { listMemberAbsences, createMemberAbsence, deleteMemberAbsence } from '@/api/absences'

const props = defineProps<{
  memberId: number
}>()

const { t } = useI18n()

const absences = ref<MemberAbsence[]>([])
const loading = ref(true)
const error = ref('')
const success = ref('')
const saving = ref(false)

const newFrom = ref('')
const newUntil = ref('')
const newReason = ref('')

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    absences.value = await listMemberAbsences(props.memberId)
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function statusOf(a: MemberAbsence): 'active' | 'upcoming' | 'expired' {
  const today = new Date().toISOString().slice(0, 10)
  if (a.absentUntil && a.absentUntil < today) return 'expired'
  if (a.absentFrom && a.absentFrom > today) return 'upcoming'
  return 'active'
}

const canSave = computed(() => newFrom.value && newUntil.value)

async function create() {
  if (!canSave.value) return
  saving.value = true
  error.value = ''
  try {
    await createMemberAbsence({
      memberId: props.memberId,
      absentFrom: newFrom.value,
      absentUntil: newUntil.value,
      reason: newReason.value.trim() || undefined,
    })
    newFrom.value = ''
    newUntil.value = ''
    newReason.value = ''
    absences.value = await listMemberAbsences(props.memberId)
    success.value = t('memberDetail.absences.saved')
    setTimeout(() => { success.value = '' }, 3000)
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

async function remove(id: number) {
  error.value = ''
  try {
    await deleteMemberAbsence(id)
    absences.value = await listMemberAbsences(props.memberId)
  } catch {
    error.value = t('common.error')
  }
}

function formatDate(d: string | undefined): string {
  if (!d) return '-'
  return new Date(d).toLocaleDateString()
}

onMounted(loadData)
</script>

<template>
  <div class="space-y-6">
    <Spinner v-if="loading" size="md" />
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <Alert v-if="success" variant="success">{{ success }}</Alert>

    <template v-if="!loading">
      <NeutralContainer class="space-y-4">
        <SubHeader>{{ t('memberDetail.absences.title') }}</SubHeader>
        <EmptyState v-if="absences.length === 0" compact>{{ t('memberDetail.absences.empty') }}</EmptyState>
        <div v-for="a in absences" :key="a.id" class="flex items-center justify-between rounded-lg px-4 py-3 bg-bg-light-accent/40 dark:bg-bg-dark-accent/40">
          <div class="space-y-1">
            <div class="flex items-center gap-2">
              <span class="text-sm font-medium">{{ formatDate(a.absentFrom) }} – {{ formatDate(a.absentUntil) }}</span>
              <SuccessBadge v-if="statusOf(a) === 'active'">{{ t('memberDetail.absences.active') }}</SuccessBadge>
              <InfoBadge v-else-if="statusOf(a) === 'upcoming'">{{ t('memberDetail.absences.upcoming') }}</InfoBadge>
              <SecondaryBadge v-else>{{ t('memberDetail.absences.expired') }}</SecondaryBadge>
            </div>
            <p v-if="a.reason" class="text-xs text-(--text-muted)">{{ a.reason }}</p>
          </div>
          <DeleteButton @click="remove(a.id)" />
        </div>
      </NeutralContainer>

      <NeutralContainer class="space-y-4">
        <SubHeader>{{ t('memberDetail.absences.add') }}</SubHeader>
        <div class="grid gap-3 sm:grid-cols-3">
          <div class="space-y-1">
            <FieldLabel>{{ t('memberDetail.absences.from') }}</FieldLabel>
            <DateInput v-model="newFrom" />
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('memberDetail.absences.until') }}</FieldLabel>
            <DateInput v-model="newUntil" />
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('memberDetail.absences.reason') }}</FieldLabel>
            <TextInput v-model="newReason" :placeholder="t('memberDetail.absences.reasonPlaceholder')" />
          </div>
        </div>
        <PrimaryButton :icon="['fas', 'plus']" :disabled="saving || !canSave" @click="create">
          {{ saving ? t('common.loading') : t('memberDetail.absences.add') }}
        </PrimaryButton>
      </NeutralContainer>
    </template>
  </div>
</template>
