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
import StationBadge from '@/components/badge/StationBadge.vue'
import IconButton from '@/components/button/IconButton.vue'
import BulletList from '@/components/typography/BulletList.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.basics.federation.title')" :subtitle="t('helpCenter.basics.federation.subtitle')">
    <HelpSection :title="t('helpCenter.basics.federation.whatIs')">
      <p>{{ t('helpCenter.basics.federation.whatIsText') }}</p>
      <p>{{ t('helpCenter.basics.federation.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.federation.whatShared')">
      <BulletList>
        <li>{{ t('helpCenter.basics.federation.shared1') }}</li>
        <li>{{ t('helpCenter.basics.federation.shared2') }}</li>
        <li>{{ t('helpCenter.basics.federation.shared3') }}</li>
        <li>{{ t('helpCenter.basics.federation.shared4') }}</li>
      </BulletList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.federation.howRecognize')">
      <p>{{ t('helpCenter.basics.federation.howRecognizeText') }}</p>
    </HelpSection>

    <!-- Dummy: Content with station badge -->
    <NeutralContainer>
      <div class="space-y-3">
        <div class="flex items-center gap-2">
          <font-awesome-icon :icon="['fas', 'file-lines']" class="text-xl text-primary" />
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2">
              <span class="text-sm font-medium">Grundlagen PSA</span>
              <StationBadge station-name="JF Partnerwache" />
            </div>
            <span class="text-xs text-(--text-muted)">Schutzausrüstung erklärt</span>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <font-awesome-icon :icon="['fas', 'file-lines']" class="text-xl text-(--text-muted)" />
          <div class="flex-1 min-w-0">
            <span class="text-sm font-medium">Knotenkunde</span>
            <p class="text-xs text-(--text-muted)">Eigene Datei — kein Badge</p>
          </div>
        </div>
      </div>
    </NeutralContainer>

    <MutedText tag="p" class="mt-1">{{ t('helpCenter.basics.federation.badgeHint') }}</MutedText>

    <HelpSection :title="t('helpCenter.basics.federation.viewTitle')">
      <p>{{ t('helpCenter.basics.federation.viewText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.federation.copyTitle')">
      <p>{{ t('helpCenter.basics.federation.copyText') }}</p>
    </HelpSection>

    <!-- Dummy: Copy button example -->
    <NeutralContainer>
      <div class="flex items-center gap-2">
        <font-awesome-icon :icon="['fas', 'book']" class="text-xl text-primary" />
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2">
            <span class="text-sm font-medium">Erste Hilfe Grundlagen</span>
            <StationBadge station-name="JF Partnerwache" />
          </div>
          <span class="text-xs text-(--text-muted)">Erste Hilfe Kurs</span>
        </div>
        <IconButton :icon="['fas', 'copy']" :label="t('federation.copyToStation')" />
      </div>
    </NeutralContainer>

    <MutedText tag="p" class="mt-1">{{ t('helpCenter.basics.federation.copyHint') }}</MutedText>

    <HelpSection :title="t('helpCenter.basics.federation.editTitle')">
      <p>{{ t('helpCenter.basics.federation.editText') }}</p>
      <p>{{ t('helpCenter.basics.federation.editText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.federation.setupTitle')">
      <p>{{ t('helpCenter.basics.federation.setupText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.basics.federation.tip') }}</HelpTip>
  </HelpArticle>
</template>
