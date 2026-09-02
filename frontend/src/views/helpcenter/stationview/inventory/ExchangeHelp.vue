/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import HelpRoleToggle from '@/components/helpcenter/HelpRoleToggle.vue'
import type {HelpPerspective} from '@/components/helpcenter/HelpRoleToggle.vue'
import {StationPermission} from '@/api/types'
import ExchangeTableDummy from './exchangehelp/ExchangeTableDummy.vue'

const {t} = useI18n()

const perspectives: HelpPerspective[] = [
  {key: 'member', label: t('helpCenter.roles.member'), permissions: [StationPermission.USER]},
  {key: 'guardian', label: t('helpCenter.roles.memberManager'), permissions: [StationPermission.MEMBER_GUARDIAN]},
  {key: 'manager', label: t('helpCenter.roles.manager'), permissions: [StationPermission.INVENTORY_EXCHANGE]},
]
const activeView = ref('')
const managerView = computed(() => activeView.value === 'manager')
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryExchanges.title')" :subtitle="t('helpCenter.inventoryExchanges.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryExchanges.whatIs')">
      <p>{{ t('helpCenter.inventoryExchanges.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryExchanges.statusAnnounced') }}</p>
      <p>{{ t('helpCenter.inventoryExchanges.statusReceived') }}</p>
      <p>{{ t('helpCenter.inventoryExchanges.statusShipped') }}</p>
      <p>{{ t('helpCenter.inventoryExchanges.statusArrived') }}</p>
      <p>{{ t('helpCenter.inventoryExchanges.statusExchanged') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryExchanges.filterTitle')">
      <p>{{ t('helpCenter.inventoryExchanges.filterText') }}</p>
      <p>{{ t('helpCenter.inventoryExchanges.filterDefaultText') }}</p>
      <p>{{ t('helpCenter.inventoryExchanges.filterSortText') }}</p>
      <p>{{ t('helpCenter.inventoryExchanges.filterExportText') }}</p>
    </HelpSection>

    <HelpRoleToggle v-model="activeView" :perspectives="perspectives"/>

    <ExchangeTableDummy :manager-view="managerView"/>

    <HelpSection v-if="activeView === 'member' || activeView === ''" :title="t('helpCenter.inventoryExchanges.asMember')">
      <p>{{ t('helpCenter.inventoryExchanges.asMemberText') }}</p>
    </HelpSection>

    <HelpSection v-if="activeView === 'guardian'" :title="t('helpCenter.inventoryExchanges.asMemberManager')">
      <p>{{ t('helpCenter.inventoryExchanges.asMemberManagerText') }}</p>
    </HelpSection>

    <HelpSection v-if="activeView === 'manager'" :title="t('helpCenter.inventoryExchanges.asManager')">
      <p>{{ t('helpCenter.inventoryExchanges.asManagerText') }}</p>
      <p>{{ t('helpCenter.inventoryExchanges.managerStatusChange') }}</p>
      <p>{{ t('helpCenter.inventoryExchanges.managerAssignItem') }}</p>
      <p>{{ t('helpCenter.inventoryExchanges.managerCreateProcurement') }}</p>
      <p>{{ t('helpCenter.inventoryExchanges.managerExport') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryExchanges.tip') }}</HelpTip>
  </HelpArticle>
</template>
