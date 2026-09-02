/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import {StationModules, StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import NotificationsPanel from './overviewview/NotificationsPanel.vue'
import ExchangesPanel from './overviewview/ExchangesPanel.vue'
import SelfChecksPanel from './overviewview/SelfChecksPanel.vue'
import AwaitingAnswerPanel from './overviewview/AwaitingAnswerPanel.vue'
import RegistrationsPanel from './overviewview/RegistrationsPanel.vue'
import UpcomingEventsPanel from './overviewview/UpcomingEventsPanel.vue'
import SetupChecklist from './overviewview/SetupChecklist.vue'
import OnboardingTaskCard from '@/components/onboarding/OnboardingTaskCard.vue'

const {t} = useI18n()
const router = useRouter()
const {isModuleEnabled, sessionInfo, hasPermission} = useSession()

const profileIncomplete = computed(() => sessionInfo.value?.profileComplete === false)
</script>

<template>
  <ViewContent :title="t('pages.dashboard-overview.title')" :subtitle="t('pages.dashboard-overview.subtitle')">
    <div class="space-y-6">
      <SetupChecklist/>
      <OnboardingTaskCard v-if="hasPermission(StationPermission.STATION_ADMINISTRATOR)" level="STATION"/>
      <OnboardingTaskCard level="MEMBER"/>
      <!-- Onboarding banner -->
      <ErrorContainer v-if="profileIncomplete" class="flex items-center justify-between gap-4">
        <div>
          <p class="font-semibold text-sm">{{ t('dashboard.profileIncomplete') }}</p>
          <p class="text-xs">{{ t('dashboard.profileIncompleteHint') }}</p>
        </div>
        <PrimaryButton class="shrink-0" @click="router.push({ name: 'profile' })">
          {{ t('dashboard.completeProfile') }}
        </PrimaryButton>
      </ErrorContainer>

      <!-- Tile layout for all panels -->
      <div class="grid grid-cols-1 lg:grid-cols-2 2xl:grid-cols-3 gap-6">
        <NotificationsPanel/>
        <ExchangesPanel v-if="isModuleEnabled(StationModules.INVENTORY)"/>
        <SelfChecksPanel v-if="isModuleEnabled(StationModules.INVENTORY)"/>
        <AwaitingAnswerPanel v-if="isModuleEnabled(StationModules.EVENTS)"/>
        <RegistrationsPanel v-if="isModuleEnabled(StationModules.EVENTS)"/>
        <UpcomingEventsPanel v-if="isModuleEnabled(StationModules.EVENTS)"/>
      </div>
    </div>
  </ViewContent>
</template>
