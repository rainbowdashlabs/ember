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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import type {CalendarBar, CalendarBlock} from '@/api/generated/schema'
import {provideInventoryRoutes, STATION_INVENTORY_ROUTES} from '@/composables/useInventoryRoutes'
import CalendarBarLegend from '@/views/stationview/inventory/lendingcalendarview/CalendarBarLegend.vue'
import CalendarHeader from '@/views/stationview/inventory/lendingcalendarview/CalendarHeader.vue'
import CalendarTrack from '@/views/stationview/inventory/lendingcalendarview/CalendarTrack.vue'
import {daysFrom} from '@/views/stationview/inventory/lendingcalendarview/calendarGrid'

const {t} = useI18n()
provideInventoryRoutes({...STATION_INVENTORY_ROUTES, lendingRequest: undefined})

const days = daysFrom('2026-06-01')
const bars: CalendarBar[] = [
  {requestId: 1, inventoryId: 1, artId: null, partner: 'DLRG Neustadt', quantity: 2, status: 'REQUESTED', overdue: false, from: '2026-06-03', to: '2026-06-06'},
  {requestId: 2, inventoryId: 1, artId: null, partner: 'DLRG Musterstadt', quantity: 2, status: 'APPROVED', overdue: false, from: '2026-06-05', to: '2026-06-09'},
  {requestId: 3, inventoryId: 1, artId: null, partner: 'DLRG Hafenstadt', quantity: 1, status: 'LENT', overdue: false, from: '2026-06-12', to: '2026-06-15'},
]
const blocks: CalendarBlock[] = [{id: 1, inventoryId: 1, artId: null, itemId: null, pieceName: null, from: '2026-06-20', to: '2026-06-22', reason: 'Kreisfeuerwehrtag'}]
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryLendingCalendar.title')" :subtitle="t('helpCenter.inventoryLendingCalendar.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryLendingCalendar.whatIs')">
      <p>{{ t('helpCenter.inventoryLendingCalendar.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingCalendar.barsText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingCalendar.clashText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.inventoryLendingCalendar.exampleTitle')">
      <NeutralContainer class="space-y-3 overflow-x-auto">
        <CalendarBarLegend/>
        <div class="min-w-[48rem] space-y-1">
          <CalendarHeader :days="days" today="2026-06-04" :dragging="null"/>
          <CalendarTrack :row="{inventoryId: 1, artId: null, pieceCount: 3}" :days="days" :bars="bars" :blocks="blocks"
                         :dragging="null" :draggable="false"/>
        </div>
      </NeutralContainer>
    </HelpSection>
    <HelpSection :title="t('helpCenter.inventoryLendingCalendar.blockTitle')">
      <p>{{ t('helpCenter.inventoryLendingCalendar.blockText') }}</p>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.inventoryLendingCalendar.tip') }}</HelpTip>
  </HelpArticle>
</template>
