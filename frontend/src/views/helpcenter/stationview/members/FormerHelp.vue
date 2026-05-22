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

    <!-- Dummy: Former members table -->
    <SectionHeader>{{ t('formerMembers.title') }}</SectionHeader>

    <NeutralContainer class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
            <th class="px-3 py-2 font-medium">{{ t('membersList.colName') }}</th>
            <th class="px-3 py-2 font-medium">{{ t('membersList.colEmail') }}</th>
            <th class="px-3 py-2"></th>
          </tr>
        </thead>
        <tbody>
          <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
            <td class="px-3 py-2.5 font-medium text-(--text-muted)">Jan Müller</td>
            <td class="px-3 py-2.5 text-(--text-muted)">jan@example.com</td>
            <td class="px-3 py-2.5 text-right">
              <PrimaryButton>
                <font-awesome-icon :icon="['fas', 'user-check']" class="mr-1"/>
                {{ t('formerMembers.reactivate') }}
              </PrimaryButton>
            </td>
          </tr>
          <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
            <td class="px-3 py-2.5 font-medium text-(--text-muted)">Sara Klein</td>
            <td class="px-3 py-2.5 text-(--text-muted)">sara@example.com</td>
            <td class="px-3 py-2.5 text-right">
              <PrimaryButton>
                <font-awesome-icon :icon="['fas', 'user-check']" class="mr-1"/>
                {{ t('formerMembers.reactivate') }}
              </PrimaryButton>
            </td>
          </tr>
        </tbody>
      </table>
    </NeutralContainer>

    <p class="text-xs text-(--text-muted)">2 {{ t('formerMembers.count') }}</p>

    <HelpTip>{{ t('helpCenter.membersFormer.tip') }}</HelpTip>
  </HelpArticle>
</template>
