/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import { emberLogoFaq } from '@/composables/useEmberLogo'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const {t} = useI18n()
const faqLogo = emberLogoFaq()

const sections = [
  {icon: ['fas', 'circle-info'], route: 'help-basics-overview', key: 'overview'},
  {icon: ['fas', 'shield'], route: 'help-basics-permissions', key: 'permissions'},
  {icon: ['fas', 'puzzle-piece'], route: 'help-basics-modules', key: 'modules'},
  {icon: ['fas', 'server'], route: 'help-basics-hosting', key: 'hosting'},
  {icon: ['fas', 'arrow-right-arrow-left'], route: 'help-basics-federation', key: 'federation'},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.welcome.title')" :subtitle="t('helpCenter.welcome.subtitle')">
    <div class="flex justify-center mb-4">
      <LayeredEmberLogo :layers="faqLogo.layers" :active-layers="faqLogo.activeLayers" :auto-blink="true" :bounce="true" :display-shake="true" size="w-32 h-32" :pixel-size="512" />
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
          <NeutralContainer class="flex items-center gap-2 p-4 cursor-pointer hover:bg-(--bg-accent) transition-colors">
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
          <PrimaryButton :icon="['fas', 'gauge']">
            {{ t('helpCenter.welcome.goToStation') }}
          </PrimaryButton>
        </router-link>
        <router-link :to="{name: 'help-admin-module-overview'}">
          <SecondaryButton :icon="['fas', 'shield']">
            {{ t('helpCenter.welcome.goToAdmin') }}
          </SecondaryButton>
        </router-link>
      </div>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.welcome.tip') }}</HelpTip>
  </HelpArticle>
</template>
