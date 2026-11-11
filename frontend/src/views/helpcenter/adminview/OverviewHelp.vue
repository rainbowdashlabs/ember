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
import SubHeader from '@/components/typography/SubHeader.vue'
import AttentionCard from '@/views/adminview/adminoverviewview/AttentionCard.vue'

const {t} = useI18n()

/** Four cards of the attention grid as the overview draws them: one critical, two warning, one clear. */
const cards = [
  {key: 'emailFailed', icon: ['fas', 'envelope'], label: 'adminOverview.cards.emailFailed', count: 3, critAt: 1},
  {key: 'stationsSetupPending', icon: ['fas', 'building'], label: 'adminOverview.cards.stationsSetupPending', count: 2, warnAt: 1},
  {key: 'accountsUnverified', icon: ['fas', 'user-clock'], label: 'adminOverview.cards.accountsUnverified', count: 7, warnAt: 5, critAt: 50},
  {key: 'emailStuck', icon: ['fas', 'hourglass-half'], label: 'adminOverview.cards.emailStuck', count: 0, critAt: 1},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminOverview.title')" :subtitle="t('helpCenter.adminOverview.subtitle')">
    <HelpSection :title="t('helpCenter.adminOverview.whatIs')">
      <p>{{ t('helpCenter.adminOverview.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminOverview.whoCanSee')">
      <p>{{ t('helpCenter.adminOverview.whoCanSeeText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminOverview.cardsTitle')">
      <p>{{ t('helpCenter.adminOverview.cardsText') }}</p>
      <p>{{ t('helpCenter.adminOverview.allClearText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminOverview.exampleTitle')">
      <SubHeader>{{ t('adminOverview.attentionSection') }}</SubHeader>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 mt-3">
        <AttentionCard
            v-for="card in cards"
            :key="card.key"
            :icon="card.icon"
            :label="t(card.label)"
            :count="card.count"
            :warn-at="card.warnAt"
            :crit-at="card.critAt"
        />
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminOverview.mailsTitle')">
      <p>{{ t('helpCenter.adminOverview.failedText') }}</p>
      <p>{{ t('helpCenter.adminOverview.acknowledgeText') }}</p>
      <p>{{ t('helpCenter.adminOverview.queueText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminOverview.stationsTitle')">
      <p>{{ t('helpCenter.adminOverview.stationsText') }}</p>
      <p>{{ t('helpCenter.adminOverview.stationsLinkText') }}</p>
      <p>{{ t('helpCenter.adminOverview.accountsText') }}</p>
      <p>{{ t('helpCenter.adminOverview.otherText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminOverview.tip') }}</HelpTip>
  </HelpArticle>
</template>
