/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {StationMember} from '@/api/types'
import {absences, managedMembers as managedMembersApi} from '@/api'
import type {MemberAbsence} from '@/api/absences'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import AbsenceAddForm from './absenceview/AbsenceAddForm.vue'
import AbsenceListItem from './absenceview/AbsenceListItem.vue'

const {t} = useI18n()
const {sessionInfo, loaded, isGuardian} = useSession()

const myAbsences = ref<MemberAbsence[]>([])
const managedMembers = ref<StationMember[]>([])
const success = ref('')

const showAddAbsence = ref(false)

const currentMemberId = computed(() => sessionInfo.value?.member?.id ?? 0)

const {loading, error, reload} = useAsyncLoader(async () => {
  if (!loaded.value) return
  myAbsences.value = await absences.listMyAbsences()
  if (isGuardian()) {
    const managed = await managedMembersApi.listManaged()
    managedMembers.value = managed.map(m => ({
      id: m.id,
      stationId: m.stationId,
      accountId: m.accountId,
      name: m.name,
      email: m.email,
    }))
  }
})

const {running: savingAbsence, error: addError, run: addAbsence} = useAsyncAction(
    async (payload: {absentFrom: string; absentUntil: string; reason?: string; memberIds?: number[]}) => {
      success.value = ''
      await absences.createAbsence(payload)
      myAbsences.value = await absences.listMyAbsences()
      showAddAbsence.value = false
      success.value = t('profile.absenceCreated')
    },
    {formatError: () => t('common.error')},
)

const {error: removeError, run: removeAbsence} = useAsyncAction(
    async (id: number) => {
      await absences.deleteAbsence(id)
      myAbsences.value = await absences.listMyAbsences()
    },
    {formatError: () => t('common.error')},
)

const pageError = computed(() => error.value || addError.value || removeError.value)

watch(loaded, (isLoaded) => {
  if (isLoaded) reload()
})
</script>

<template>
  <ViewContent
      :title="t('pages.profile-absences.title')"
      :subtitle="t('pages.profile-absences.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="pageError" variant="error">{{ pageError }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="!loading">
        <div class="flex items-center justify-between">
          <div/>
          <SecondaryButton :icon="['fas', 'plus']" @click="showAddAbsence = true">
            {{ t('profile.absenceAdd') }}
          </SecondaryButton>
        </div>

        <AbsenceAddForm
            v-if="showAddAbsence"
            :current-member-id="currentMemberId"
            :managed-members="managedMembers"
            :saving="savingAbsence"
            @save="addAbsence"
            @cancel="showAddAbsence = false"
        />

        <div v-if="myAbsences.length === 0 && !showAddAbsence" class="text-(--text-muted) text-sm text-center py-4">
          {{ t('profile.absenceEmpty') }}
        </div>

        <div class="space-y-2">
          <AbsenceListItem
              v-for="absence in myAbsences"
              :key="absence.id"
              :absence="absence"
              @remove="removeAbsence"
          />
        </div>
      </template>
    </div>
  </ViewContent>
</template>
