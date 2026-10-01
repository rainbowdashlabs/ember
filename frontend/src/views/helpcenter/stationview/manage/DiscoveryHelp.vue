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
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import DiscoveryHelpSampleCard from '@/views/helpcenter/stationview/manage/discoveryhelp/DiscoveryHelpSampleCard.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.discovery.title')" :subtitle="t('helpCenter.discovery.subtitle')">
    <HelpSection :title="t('helpCenter.discovery.whatIs')">
      <p>{{ t('helpCenter.discovery.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.discovery.connectTitle')">
      <p>{{ t('helpCenter.discovery.connectText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.discovery.exampleTitle')">
      <p>{{ t('helpCenter.discovery.exampleText') }}</p>
    </HelpSection>

    <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <DiscoveryHelpSampleCard
          :name="t('helpCenter.sample.stations.jfMusterstadt')"
          :text="t('helpCenter.sample.stations.jfMusterstadtText')"
      >
        <PrimaryBadge>{{ t('discovery.ownStation') }}</PrimaryBadge>
      </DiscoveryHelpSampleCard>
      <DiscoveryHelpSampleCard
          :name="t('helpCenter.sample.stations.jfBeispieldorf')"
          :text="t('helpCenter.sample.stations.jfBeispieldorfText')"
      >
        <SuccessBadge>{{ t('discovery.alreadyConnected') }}</SuccessBadge>
      </DiscoveryHelpSampleCard>
      <DiscoveryHelpSampleCard :name="t('helpCenter.sample.stations.jfAltenburg')">
        <PrimaryButton compact disabled>
          <font-awesome-icon :icon="['fas', 'handshake']" class="mr-1"/>
          {{ t('discovery.connect') }}
        </PrimaryButton>
      </DiscoveryHelpSampleCard>
      <DiscoveryHelpSampleCard
          :name="t('helpCenter.sample.stations.north')"
          :host="t('helpCenter.discovery.sampleHost')"
      >
        <MutedText size="sm">
          <font-awesome-icon :icon="['fas', 'globe']" class="mr-1"/>
          {{ t('discovery.visitRemoteStation') }}
        </MutedText>
      </DiscoveryHelpSampleCard>
    </div>

    <HelpSection :title="t('helpCenter.discovery.remoteTitle')">
      <p>{{ t('helpCenter.discovery.remoteText') }}</p>
      <p>{{ t('helpCenter.discovery.remoteListText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.discovery.searchTitle')">
      <p>{{ t('helpCenter.discovery.searchText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.discovery.settingsTitle')">
      <p>{{ t('helpCenter.discovery.settingsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.discovery.tip') }}</HelpTip>
  </HelpArticle>
</template>
