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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersFormer.title')" :subtitle="t('helpCenter.membersFormer.subtitle')">
    <HelpSection :title="t('helpCenter.membersFormer.whatIs')">
      <p>{{ t('helpCenter.membersFormer.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersFormer.markTitle')">
      <p>{{ t('helpCenter.membersFormer.markText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersFormer.reactivateTitle')">
      <p>{{ t('helpCenter.membersFormer.reactivateText') }}</p>
    </HelpSection>

    <!-- Dummy: Former members table with formerAt column -->
    <HelpSection :title="t('helpCenter.membersFormer.exampleTitle')">
      <SectionHeader>{{ t('formerMembers.title') }}</SectionHeader>

      <NeutralContainer class="overflow-x-auto mt-2">
        <table class="w-full text-sm">
          <thead>
            <THead>
              <Th>{{ t('membersList.colName') }}</Th>
              <Th>{{ t('membersList.colEmail') }}</Th>
              <Th>{{ t('formerMembers.colFormerAt') }}</Th>
              <th class="px-3 py-2"></th>
            </THead>
          </thead>
          <tbody>
            <TRow>
              <Td class="font-medium text-(--text-muted)">Jan Müller</Td>
              <Td muted>jan@example.com</Td>
              <Td muted>15.04.2026</Td>
              <Td align="right">
                <PrimaryButton :icon="['fas', 'user-check']">
                  {{ t('formerMembers.reactivate') }}
                </PrimaryButton>
              </Td>
            </TRow>
            <TRow>
              <Td class="font-medium text-(--text-muted)">Sara Klein</Td>
              <Td muted>sara@example.com</Td>
              <Td muted>02.03.2026</Td>
              <Td align="right">
                <PrimaryButton :icon="['fas', 'user-check']">
                  {{ t('formerMembers.reactivate') }}
                </PrimaryButton>
              </Td>
            </TRow>
          </tbody>
        </table>
      </NeutralContainer>

      <p class="text-xs text-(--text-muted) mt-1">2 {{ t('formerMembers.count') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.membersFormer.tip') }}</HelpTip>
  </HelpArticle>
</template>
