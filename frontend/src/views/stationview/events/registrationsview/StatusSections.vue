/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import StatusSection from './StatusSection.vue'
import QueueSection from '../eventshared/QueueSection.vue'
import type {RegistrationResponse, RegistrationStatsResponse, RegistrationStatus} from '@/api/generated/schema'
import type {QueueEntry} from '@/util/registrationQueue'

/**
 * The sign-ups of one date by their status: those waiting for a decision first, then the waiting
 * list in the order it moves up in, then everybody already decided.
 */
defineProps<{
  loading: boolean
  byStatus: Record<RegistrationStatus, RegistrationResponse[]>
  /** Everybody waiting for a place, first in line first, partners' members included. */
  queue: QueueEntry[]
  /** Whether a partner decides about its own people, whose rows on the waiting list then offer nothing. */
  partnerDecides: (partnerId: number) => boolean
  /** Whether the date keeps a waiting list, which only an appointment with a participant limit does. */
  canQueue: boolean
  busy: boolean
  stats: RegistrationStatsResponse[]
}>()

const emit = defineEmits<{
  accept: [regId: number]
  deny: [regId: number]
  queue: [regIds: number[]]
  reorder: [entry: QueueEntry, position: number]
  moveUp: [entry: QueueEntry]
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
          :can-queue="canQueue"
          :busy="busy"
          @accept="(id) => emit('accept', id)"
          @deny="(id) => emit('deny', id)"
          @queue="(ids) => emit('queue', ids)"
      />
      <QueueSection
          :entries="queue"
          can-manage
          :partner-decides="partnerDecides"
          :busy="busy"
          @reorder="(entry, position) => emit('reorder', entry, position)"
          @move-up="(entry) => emit('moveUp', entry)"
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
          icon="rotate-left"
          :title="t('eventsRegistrations.groupWithdrawn')"
          :registrations="byStatus.WITHDRAWN"
          :stats="stats"
      />
      <StatusSection
          icon="user-slash"
          :title="t('eventsRegistrations.groupDeclined')"
          :registrations="byStatus.DECLINED"
          :stats="stats"
          declined
      />
    </template>
  </div>
</template>
