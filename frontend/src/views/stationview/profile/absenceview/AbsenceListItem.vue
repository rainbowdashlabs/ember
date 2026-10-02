/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {AbsenceResponse} from '@/api/generated/schema'
import {formatDate, todayIsoDate} from '@/util/format'

const props = defineProps<{
  absence: AbsenceResponse
}>()

const emit = defineEmits<{
  (e: 'remove', id: number): void
}>()

const {t} = useI18n()

function isAbsenceActive(absence: AbsenceResponse): boolean {
  const today = todayIsoDate()
  return absence.absentFrom <= today && absence.absentUntil >= today
}

function isAbsenceUpcoming(absence: AbsenceResponse): boolean {
  return absence.absentFrom > todayIsoDate()
}

</script>

<template>
  <NeutralContainer>
    <div class="flex items-center justify-between flex-wrap gap-2">
      <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
        <span v-if="absence.memberIdentity"
              class="text-sm font-semibold"><MemberName :identity="absence.memberIdentity"/></span>
        <span class="text-sm">{{ formatDate(absence.absentFrom) }} – {{
            formatDate(absence.absentUntil)
          }}</span>
        <MutedText v-if="absence.reason" size="sm">{{ absence.reason }}</MutedText>
        <span v-if="absence.createdByName" class="text-xs text-(--text-muted) italic">{{
            t('common.createdBy', {name: absence.createdByName})
          }}</span>
      </div>
      <div class="flex items-center gap-2">
        <SuccessBadge v-if="isAbsenceActive(absence)">{{ t('profile.absenceActive') }}</SuccessBadge>
        <InfoBadge v-else-if="isAbsenceUpcoming(absence)">{{ t('profile.absenceUpcoming') }}</InfoBadge>
        <ErrorBadge v-else>{{ t('profile.absenceExpired') }}</ErrorBadge>
        <DeleteButton @click="emit('remove', props.absence.id)"/>
      </div>
    </div>
  </NeutralContainer>
</template>
