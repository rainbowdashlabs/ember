/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import StatusSection from './StatusSection.vue'
import type {EventRegistrationEntry, MemberRegistrationStats} from '@/api/events'

type StatusKey = 'PENDING' | 'ACCEPTED' | 'DENIED' | 'DECLINED' | 'WITHDRAWN'

defineProps<{
  loading: boolean
  byStatus: Record<StatusKey, EventRegistrationEntry[]>
  stats: MemberRegistrationStats[]
}>()

const emit = defineEmits<{
  accept: [regId: number]
  deny: [regId: number]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="border-t border-(--border) pt-3 space-y-4" @click.stop>
    <Spinner v-if="loading" size="md"/>
    <template v-else>
      <StatusSection
          icon="hourglass-half"
          :title="t('eventsRegistrations.groupPending')"
          :registrations="byStatus.PENDING"
          :stats="stats"
          show-actions
          @accept="(id) => emit('accept', id)"
          @deny="(id) => emit('deny', id)"
      />
      <StatusSection
          icon="check"
          :title="t('eventsRegistrations.groupAccepted')"
          :registrations="byStatus.ACCEPTED"
          :stats="stats"
      />
      <StatusSection
          icon="xmark"
          :title="t('eventsRegistrations.groupDenied')"
          :registrations="byStatus.DENIED"
          :stats="stats"
      />
      <StatusSection
          icon="user-slash"
          :title="t('eventsRegistrations.groupDeclined')"
          :registrations="byStatus.DECLINED"
          :stats="stats"
      />
      <StatusSection
          icon="rotate-left"
          :title="t('eventsRegistrations.groupWithdrawn')"
          :registrations="byStatus.WITHDRAWN"
          :stats="stats"
      />
    </template>
  </div>
</template>
