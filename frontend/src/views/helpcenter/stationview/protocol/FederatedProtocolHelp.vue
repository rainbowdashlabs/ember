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
import StationBadge from '@/components/badge/StationBadge.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.federatedProtocol.title')"
               :subtitle="t('helpCenter.federatedProtocol.subtitle')">
    <HelpSection :title="t('helpCenter.federatedProtocol.whatIs')">
      <p>{{ t('helpCenter.federatedProtocol.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedProtocol.howTo')">
      <p>{{ t('helpCenter.federatedProtocol.howToStep1') }}</p>
      <p>{{ t('helpCenter.federatedProtocol.howToStep2') }}</p>

      <!-- Dummy: shared protocol with sections -->
      <NeutralContainer class="mt-4 space-y-3">
        <div class="flex items-center justify-between gap-4">
          <div class="flex items-center gap-2 flex-wrap">
            <span class="font-medium">Leistungsabzeichen Bronze</span>
            <StationBadge station-name="Wache Nordstadt"/>
          </div>
          <PrimaryButton compact disabled>
            <font-awesome-icon :icon="['fas', 'copy']"/>
            {{ t('federation.copyToStation') }}
          </PrimaryButton>
        </div>
        <div>
          <SubHeader class="!mb-1">Knoten und Stiche</SubHeader>
          <ul class="space-y-1 text-sm">
            <li class="flex items-center justify-between gap-4">
              <span>Mastwurf</span>
              <span class="text-(--text-muted)">5</span>
            </li>
            <li class="flex items-center justify-between gap-4">
              <span>Schotenstich</span>
              <span class="text-(--text-muted)">5</span>
            </li>
          </ul>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedProtocol.editTitle')">
      <p>{{ t('helpCenter.federatedProtocol.editText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.federatedProtocol.tip') }}</HelpTip>
  </HelpArticle>
</template>
