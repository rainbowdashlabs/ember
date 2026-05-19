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
import type {HelpRole} from '@/components/helpcenter/HelpRoleToggle.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'

const {t} = useI18n()

const roles: HelpRole[] = [
  {key: 'member', label: t('helpCenter.roles.member')},
  {key: 'guardian', label: t('helpCenter.roles.memberManager')},
  {key: 'manager', label: t('helpCenter.roles.manager')},
]
const activeRole = ref('')
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

    <HelpRoleToggle v-model="activeRole" :roles="roles" />

    <!-- Dummy: Exchange table -->
    <NeutralContainer class="space-y-3">
      <div class="flex items-center justify-between flex-wrap gap-2">
        <SectionHeader>{{ t('exchanges.title') }}</SectionHeader>
        <PrimaryButton>
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-2" />
          {{ t('exchanges.create') }}
        </PrimaryButton>
      </div>
      <NeutralContainer class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
              <th v-if="activeRole === 'manager'" class="px-3 py-2 font-medium">{{ t('exchanges.colMember') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('exchanges.colInventory') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('exchanges.colOldSize') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('exchanges.colNewSize') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('exchanges.colStatus') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('exchanges.colReason') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('exchanges.colDate') }}</th>
              <th class="px-3 py-2"></th>
            </tr>
          </thead>
          <tbody>
            <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
              <td v-if="activeRole === 'manager'" class="px-3 py-2.5 text-primary">Max Mustermann</td>
              <td class="px-3 py-2.5 font-medium">Helme</td>
              <td class="px-3 py-2.5">M</td>
              <td class="px-3 py-2.5">L</td>
              <td class="px-3 py-2.5"><InfoBadge>{{ t('exchanges.status.ANNOUNCED') }}</InfoBadge></td>
              <td class="px-3 py-2.5 text-(--text-muted)">Helm passt nicht mehr</td>
              <td class="px-3 py-2.5 text-(--text-muted)">14.05.2026</td>
              <td class="px-3 py-2.5 text-right">
                <div class="flex items-center justify-end gap-1">
                  <SecondaryButton class="text-xs"><font-awesome-icon :icon="['fas', 'clock-rotate-left']" /></SecondaryButton>
                  <template v-if="activeRole === 'manager'">
                    <SecondaryButton class="text-xs"><font-awesome-icon :icon="['fas', 'arrow-right']" /></SecondaryButton>
                    <DeleteButton />
                  </template>
                </div>
              </td>
            </tr>
            <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
              <td v-if="activeRole === 'manager'" class="px-3 py-2.5 text-primary">Erika Musterfrau</td>
              <td class="px-3 py-2.5 font-medium">Jacken</td>
              <td class="px-3 py-2.5">S</td>
              <td class="px-3 py-2.5">M</td>
              <td class="px-3 py-2.5"><PrimaryBadge>{{ t('exchanges.status.RECEIVED') }}</PrimaryBadge></td>
              <td class="px-3 py-2.5 text-(--text-muted)">Neue Jacke benötigt</td>
              <td class="px-3 py-2.5 text-(--text-muted)">10.05.2026</td>
              <td class="px-3 py-2.5 text-right">
                <div class="flex items-center justify-end gap-1">
                  <SecondaryButton class="text-xs"><font-awesome-icon :icon="['fas', 'clock-rotate-left']" /></SecondaryButton>
                  <template v-if="activeRole === 'manager'">
                    <SecondaryButton class="text-xs"><font-awesome-icon :icon="['fas', 'arrow-right']" /></SecondaryButton>
                    <DeleteButton />
                  </template>
                </div>
              </td>
            </tr>
            <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
              <td v-if="activeRole === 'manager'" class="px-3 py-2.5 text-primary">Jan Schmidt</td>
              <td class="px-3 py-2.5 font-medium">Stiefel</td>
              <td class="px-3 py-2.5">42</td>
              <td class="px-3 py-2.5">44</td>
              <td class="px-3 py-2.5"><SuccessBadge>{{ t('exchanges.status.EXCHANGED') }}</SuccessBadge></td>
              <td class="px-3 py-2.5 text-(--text-muted)">Gewachsen</td>
              <td class="px-3 py-2.5 text-(--text-muted)">01.04.2026</td>
              <td class="px-3 py-2.5 text-right">
                <div class="flex items-center justify-end gap-1">
                  <SecondaryButton class="text-xs"><font-awesome-icon :icon="['fas', 'clock-rotate-left']" /></SecondaryButton>
                  <template v-if="activeRole === 'manager'">
                    <DeleteButton />
                  </template>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </NeutralContainer>
    </NeutralContainer>

    <template v-if="activeRole === 'member' || activeRole === ''">
      <HelpSection :title="t('helpCenter.inventoryExchanges.asMember')">
        <p>{{ t('helpCenter.inventoryExchanges.asMemberText') }}</p>
      </HelpSection>
    </template>

    <template v-if="activeRole === 'guardian'">
      <HelpSection :title="t('helpCenter.inventoryExchanges.asMemberManager')">
        <p>{{ t('helpCenter.inventoryExchanges.asMemberManagerText') }}</p>
      </HelpSection>
    </template>

    <template v-if="activeRole === 'manager'">
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
