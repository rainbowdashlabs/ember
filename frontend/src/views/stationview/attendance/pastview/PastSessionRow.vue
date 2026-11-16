/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {SessionSummary} from '@/api/generated/schema'
import {formatDate, formatTime} from '@/util/format'

/** One past sheet: its title, when the appointment was, and how many came, opening the sheet on a press. */
defineProps<{
  session: SessionSummary
}>()

const {t} = useI18n()
</script>

<template>
  <RowLink
      :to="{name: 'attendance-session', params: {id: session.id}}"
  >
    <NeutralContainer
        data-testid="attendance-session"
        :data-session="session.id"
        class="cursor-pointer hover:ring-2 hover:ring-primary/30 transition-all"
    >
      <div class="flex items-center justify-between flex-wrap gap-2">
        <div>
          <span class="font-semibold text-sm">{{ session.title || t('attendancePast.untitled') }}</span>
          <MutedText size="sm" class="ml-3">{{ formatDate(session.startTime) }}</MutedText>
          <MutedText class="ml-2">{{ formatTime(session.startTime) }} – {{
              formatTime(session.endTime)
            }}</MutedText>
        </div>
        <div class="flex items-center gap-2 text-xs">
          <SuccessBadge>{{ session.presentCount }} {{ t('attendancePast.present') }}</SuccessBadge>
          <ErrorBadge>{{ session.absentCount }} {{ t('attendancePast.absent') }}</ErrorBadge>
          <InfoBadge>{{ session.declinedCount }} {{ t('attendancePast.declined') }}</InfoBadge>
          <SecondaryBadge v-if="session.unconfirmedCount > 0">{{ session.unconfirmedCount }}
            {{ t('attendancePast.unconfirmed') }}
          </SecondaryBadge>
        </div>
      </div>
    </NeutralContainer>
  </RowLink>
</template>
