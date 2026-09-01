/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
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
import {createMemberAbsence, deleteMemberAbsence, listMemberAbsences, type MemberAbsence} from '@/api/absences'
import { useConfigPanel } from '@/composables/useConfigPanel'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useFlashMessage } from '@/composables/useFlashMessage'
import { formatDate, todayIsoDate } from '@/util/format'

const props = defineProps<{
  memberId: number
}>()

const { t } = useI18n()

const { config: absences, loading, error, reload: loadData } = useConfigPanel<MemberAbsence[]>({
  initial: [],
  fetch: () => listMemberAbsences(props.memberId),
})
const { message: success, flash } = useFlashMessage(3000)

const newFrom = ref('')
const newUntil = ref('')
const newReason = ref('')

function statusOf(a: MemberAbsence): 'active' | 'upcoming' | 'expired' {
  const today = todayIsoDate()
  if (a.absentUntil && a.absentUntil < today) return 'expired'
  if (a.absentFrom && a.absentFrom > today) return 'upcoming'
  return 'active'
}

const canSave = computed(() => newFrom.value && newUntil.value)

const { running: saving, error: createError, run: runCreate } = useAsyncAction(async () => {
  error.value = ''
  await createMemberAbsence({
    memberId: props.memberId,
    absentFrom: newFrom.value,
    absentUntil: newUntil.value,
    reason: newReason.value.trim() || undefined,
  })
  newFrom.value = ''
  newUntil.value = ''
  newReason.value = ''
  await loadData()
  flash(t('memberDetail.absences.saved'))
}, { formatError: () => t('common.error') })

function create() {
  if (!canSave.value) return
  runCreate()
}

async function remove(id: number) {
  error.value = ''
  try {
    await deleteMemberAbsence(id)
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}
</script>

<template>
  <div class="space-y-6">
    <Spinner v-if="loading" size="md" />
    <Alert v-if="error || createError" variant="error">{{ error || createError }}</Alert>
    <Alert v-if="success" variant="success">{{ success }}</Alert>

    <template v-if="!loading">
      <NeutralContainer class="space-y-4">
        <SubHeader>{{ t('memberDetail.absences.title') }}</SubHeader>
        <EmptyState v-if="absences.length === 0" compact>{{ t('memberDetail.absences.empty') }}</EmptyState>
        <div v-for="a in absences" :key="a.id" class="flex items-center justify-between rounded-lg px-4 py-3 bg-bg-light-accent/40 dark:bg-bg-dark-accent/40">
          <div class="space-y-1">
            <div class="flex items-center gap-2">
              <span class="text-sm font-medium">{{ formatDate(a.absentFrom) || '-' }} – {{ formatDate(a.absentUntil) || '-' }}</span>
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
