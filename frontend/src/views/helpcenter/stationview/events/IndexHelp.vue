/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import HelpPermissionGuard from '@/components/helpcenter/HelpPermissionGuard.vue'
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import ManagerSections from '@/views/helpcenter/stationview/events/indexhelp/ManagerSections.vue'
import {StationPermission} from '@/api/types'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.eventsManage.title')" :subtitle="t('helpCenter.eventsManage.subtitle')">
    <HelpSection :title="t('helpCenter.eventsManage.whatIs')">
      <p>{{ t('helpCenter.eventsManage.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventsManage.typesTitle')">
      <p>{{ t('helpCenter.eventsManage.typeRecurring') }}</p>
      <p>{{ t('helpCenter.eventsManage.typeOneTime') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventsManage.todayTitle')">
      <p>{{ t('helpCenter.eventsManage.todayText') }}</p>
      <div class="grid gap-3 sm:grid-cols-2 mt-3">
        <PrimaryContainer class="space-y-2">
          <div class="flex items-center justify-between">
            <span class="font-semibold">Übungsabend</span>
            <span class="text-sm">18:00 – 20:00</span>
          </div>
          <p class="text-sm text-(--text-muted)">Regulärer Übungsabend</p>
        </PrimaryContainer>
      </div>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.EVENT_EDIT]" :label="t('helpCenter.permissionLabel.eventEdit')">
      <ManagerSections/>
    </HelpPermissionGuard>

    <HelpTip>{{ t('helpCenter.eventsManage.tip') }}</HelpTip>
  </HelpArticle>
</template>
