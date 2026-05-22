/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminSettings.title')" :subtitle="t('helpCenter.adminSettings.subtitle')">
    <HelpSection :title="t('helpCenter.adminSettings.registrationTitle')">
      <p>{{ t('helpCenter.adminSettings.registrationText') }}</p>
    </HelpSection>

    <NeutralContainer>
      <div class="flex items-center justify-between">
        <div>
          <div class="font-medium text-sm">Wachenregistrierung</div>
          <div class="text-xs text-(--text-muted)">Neue Wachen können sich registrieren</div>
        </div>
        <ToggleInput :model-value="true" />
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.adminSettings.authTitle')">
      <p>{{ t('helpCenter.adminSettings.authText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminSettings.mailTitle')">
      <p>{{ t('helpCenter.adminSettings.mailText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminSettings.legalTitle')">
      <p>{{ t('helpCenter.adminSettings.legalText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminSettings.tip') }}</HelpTip>
  </HelpArticle>
</template>
