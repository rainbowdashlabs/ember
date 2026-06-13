/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import HelpRoleToggle from '@/components/helpcenter/HelpRoleToggle.vue'
import type {HelpPerspective} from '@/components/helpcenter/HelpRoleToggle.vue'
import {StationPermission} from '@/api/types'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'

const {t} = useI18n()

const perspectives: HelpPerspective[] = [
  {key: 'member', label: t('helpCenter.roles.member'), permissions: [StationPermission.USER]},
  {key: 'guardian', label: t('helpCenter.roles.memberManager'), permissions: [StationPermission.MEMBER_GUARDIAN]},
  {key: 'manager', label: t('helpCenter.roles.manager'), permissions: [StationPermission.INVENTORY_EXCHANGE]},
]
const activeView = ref('')
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

    <HelpRoleToggle v-model="activeView" :perspectives="perspectives" />

    <!-- Dummy: Exchange table -->
    <NeutralContainer class="space-y-3">
      <div class="flex items-center justify-between flex-wrap gap-2">
        <SectionHeader>{{ t('exchanges.title') }}</SectionHeader>
        <PrimaryButton :icon="['fas', 'plus']">
          {{ t('exchanges.create') }}
        </PrimaryButton>
      </div>
      <NeutralContainer class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <THead>
              <Th v-if="activeView === 'manager'">{{ t('exchanges.colMember') }}</Th>
              <Th>{{ t('exchanges.colInventory') }}</Th>
              <Th>{{ t('exchanges.colType') }}</Th>
              <Th>{{ t('exchanges.colOldSize') }}</Th>
              <Th>{{ t('exchanges.colNewSize') }}</Th>
              <Th>{{ t('exchanges.colStatus') }}</Th>
              <Th>{{ t('exchanges.colReason') }}</Th>
              <Th>{{ t('exchanges.colDate') }}</Th>
              <th class="px-3 py-2"></th>
            </THead>
          </thead>
          <tbody>
            <TRow>
              <Td v-if="activeView === 'manager'" class="text-primary">Max Mustermann</Td>
              <Td class="font-medium">Helme</Td>
              <Td><InfoBadge>{{ t('inventory.manage.type.INTERNAL') }}</InfoBadge></Td>
              <Td>M</Td>
              <Td>L</Td>
              <Td><InfoBadge>{{ t('exchanges.status.ANNOUNCED') }}</InfoBadge></Td>
              <Td muted>Helm passt nicht mehr</Td>
              <Td muted>14.05.2026</Td>
              <Td align="right">
                <div class="flex items-center justify-end gap-1">
                  <SecondaryButton><font-awesome-icon :icon="['fas', 'clock-rotate-left']" /></SecondaryButton>
                  <template v-if="activeView === 'manager'">
                    <SecondaryButton><font-awesome-icon :icon="['fas', 'arrow-right']" /></SecondaryButton>
                    <DeleteButton />
                  </template>
                </div>
              </Td>
            </TRow>
            <TRow>
              <Td v-if="activeView === 'manager'" class="text-primary">Erika Musterfrau</Td>
              <Td class="font-medium">Jacken</Td>
              <Td><SecondaryBadge>{{ t('inventory.manage.type.EXTERNAL') }}</SecondaryBadge></Td>
              <Td>S</Td>
              <Td>M</Td>
              <Td><PrimaryBadge>{{ t('exchanges.status.RECEIVED') }}</PrimaryBadge></Td>
              <Td muted>Neue Jacke benötigt</Td>
              <Td muted>10.05.2026</Td>
              <Td align="right">
                <div class="flex items-center justify-end gap-1">
                  <SecondaryButton><font-awesome-icon :icon="['fas', 'clock-rotate-left']" /></SecondaryButton>
                  <template v-if="activeView === 'manager'">
                    <SecondaryButton><font-awesome-icon :icon="['fas', 'arrow-right']" /></SecondaryButton>
                    <DeleteButton />
                  </template>
                </div>
              </Td>
            </TRow>
            <TRow>
              <Td v-if="activeView === 'manager'" class="text-primary">Jan Schmidt</Td>
              <Td class="font-medium">Stiefel</Td>
              <Td><InfoBadge>{{ t('inventory.manage.type.INTERNAL') }}</InfoBadge></Td>
              <Td>42</Td>
              <Td>44</Td>
              <Td><SuccessBadge>{{ t('exchanges.status.DONE') }}</SuccessBadge></Td>
              <Td muted>Gewachsen</Td>
              <Td muted>01.04.2026</Td>
              <Td align="right">
                <div class="flex items-center justify-end gap-1">
                  <SecondaryButton><font-awesome-icon :icon="['fas', 'clock-rotate-left']" /></SecondaryButton>
                  <template v-if="activeView === 'manager'">
                    <DeleteButton />
                  </template>
                </div>
              </Td>
            </TRow>
          </tbody>
        </table>
      </NeutralContainer>
    </NeutralContainer>

    <template v-if="activeView === 'member' || activeView === ''">
      <HelpSection :title="t('helpCenter.inventoryExchanges.asMember')">
        <p>{{ t('helpCenter.inventoryExchanges.asMemberText') }}</p>
      </HelpSection>
    </template>

    <template v-if="activeView === 'guardian'">
      <HelpSection :title="t('helpCenter.inventoryExchanges.asMemberManager')">
        <p>{{ t('helpCenter.inventoryExchanges.asMemberManagerText') }}</p>
      </HelpSection>
    </template>

    <template v-if="activeView === 'manager'">
      <HelpSection :title="t('helpCenter.inventoryExchanges.asManager')">
        <p>{{ t('helpCenter.inventoryExchanges.asManagerText') }}</p>
        <p>{{ t('helpCenter.inventoryExchanges.managerStatusChange') }}</p>
        <p>{{ t('helpCenter.inventoryExchanges.managerAssignItem') }}</p>
        <p>{{ t('helpCenter.inventoryExchanges.managerCreateProcurement') }}</p>
        <p>{{ t('helpCenter.inventoryExchanges.managerExport') }}</p>
      </HelpSection>
    </template>

    <HelpTip>{{ t('helpCenter.inventoryExchanges.tip') }}</HelpTip>
  </HelpArticle>
</template>
