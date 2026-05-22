/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import EmberLogo from '@/components/display/EmberLogo.vue'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const {t} = useI18n()

const sections = [
  {icon: ['fas', 'circle-info'], route: 'help-basics-overview', key: 'overview'},
  {icon: ['fas', 'shield'], route: 'help-basics-roles', key: 'roles'},
  {icon: ['fas', 'puzzle-piece'], route: 'help-basics-modules', key: 'modules'},
  {icon: ['fas', 'server'], route: 'help-basics-hosting', key: 'hosting'},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.welcome.title')" :subtitle="t('helpCenter.welcome.subtitle')">
    <div class="flex justify-center mb-4">
      <EmberLogo base="NoBG_NoGlow_FAQ" blink-base="NoBG_NoGlow_FAQ_Blink" :pixel-size="512" size="w-32 h-32" :blink="true" />
    </div>

    <HelpSection :title="t('helpCenter.welcome.whatIs')">
      <p>{{ t('helpCenter.welcome.whatIsText') }}</p>
      <p>{{ t('helpCenter.welcome.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.welcome.howToUse')">
      <p>{{ t('helpCenter.welcome.howToUseText') }}</p>
      <p>{{ t('helpCenter.welcome.howToUseText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.welcome.basicsTitle')">
      <p>{{ t('helpCenter.welcome.basicsText') }}</p>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-3">
        <router-link v-for="s in sections" :key="s.key" :to="{name: s.route}">
          <NeutralContainer class="flex items-center gap-3 p-4 cursor-pointer hover:bg-(--bg-accent) transition-colors">
            <font-awesome-icon :icon="s.icon" class="h-5 w-5 text-primary shrink-0"/>
            <div>
              <p class="font-semibold text-sm">{{ t(`helpCenter.welcome.section.${s.key}.title`) }}</p>
              <p class="text-xs text-(--text-muted)">{{ t(`helpCenter.welcome.section.${s.key}.desc`) }}</p>
            </div>
          </NeutralContainer>
        </router-link>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.welcome.otherPages')">
      <p>{{ t('helpCenter.welcome.otherPagesText') }}</p>
      <div class="flex flex-wrap gap-3 mt-3">
        <router-link :to="{name: 'help-dashboard-overview'}">
          <PrimaryButton>
            <font-awesome-icon :icon="['fas', 'gauge']" class="mr-2"/>
            {{ t('helpCenter.welcome.goToStation') }}
          </PrimaryButton>
        </router-link>
        <router-link :to="{name: 'help-admin-overview'}">
          <SecondaryButton>
            <font-awesome-icon :icon="['fas', 'shield']" class="mr-2"/>
            {{ t('helpCenter.welcome.goToAdmin') }}
          </SecondaryButton>
        </router-link>
      </div>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.welcome.tip') }}</HelpTip>
  </HelpArticle>
</template>
