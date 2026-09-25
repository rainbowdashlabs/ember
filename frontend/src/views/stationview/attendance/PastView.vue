/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {attendance} from '@/api'
import type {SessionSummary} from '@/api/attendance'
import {useSession} from '@/composables/useSession'
import MutedText from '@/components/typography/MutedText.vue'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {formatDate, formatTime} from '@/util/format'

const {t} = useI18n()
const {loaded} = useSession()

const {config: sessions, loading, failure, reload: loadData} = useConfigPanel<SessionSummary[]>({
  initial: [],
  fetch: () => attendance.listSessionSummaries(),
  immediate: false,
})

onMounted(() => {
  if (loaded.value) loadData()
})

watch(loaded, (isLoaded) => {
  if (isLoaded && loading.value) loadData()
})
</script>

<template>
  <ViewContent
      :title="t('pages.attendance-past.title')"
      :subtitle="t('pages.attendance-past.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure"/>

      <template v-if="!loading">
        <div v-if="sessions.length === 0" class="text-(--text-muted) text-sm text-center py-4">
          {{ t('attendancePast.empty') }}
        </div>

        <div class="space-y-2">
          <RowLink
              v-for="s in sessions"
              :key="s.id"
              :to="{name: 'attendance-session', params: {id: s.id}}"
          >
            <NeutralContainer
                data-testid="attendance-session"
                :data-session="s.id"
                class="cursor-pointer hover:ring-2 hover:ring-primary/30 transition-all"
            >
              <div class="flex items-center justify-between flex-wrap gap-2">
                <div>
                  <span class="font-semibold text-sm">{{ s.title || t('attendancePast.untitled') }}</span>
                  <MutedText size="sm" class="ml-3">{{ formatDate(s.startTime) }}</MutedText>
                  <MutedText class="ml-2">{{ formatTime(s.startTime) }} – {{
                      formatTime(s.endTime)
                    }}</MutedText>
                </div>
                <div class="flex items-center gap-2 text-xs">
                  <SuccessBadge>{{ s.presentCount }} {{ t('attendancePast.present') }}</SuccessBadge>
                  <ErrorBadge>{{ s.absentCount }} {{ t('attendancePast.absent') }}</ErrorBadge>
                  <InfoBadge>{{ s.declinedCount }} {{ t('attendancePast.declined') }}</InfoBadge>
                  <SecondaryBadge v-if="s.unconfirmedCount > 0">{{ s.unconfirmedCount }}
                    {{ t('attendancePast.unconfirmed') }}
                  </SecondaryBadge>
                </div>
              </div>
            </NeutralContainer>
          </RowLink>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
