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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminLegal.title')" :subtitle="t('helpCenter.adminLegal.subtitle')">
    <HelpSection :title="t('helpCenter.adminLegal.whatTitle')">
      <p>{{ t('helpCenter.adminLegal.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminLegal.howToTitle')">
      <p>{{ t('helpCenter.adminLegal.howToText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminLegal.templatesTitle')">
      <p>{{ t('helpCenter.adminLegal.templatesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminLegal.placeholdersTitle')">
      <p>{{ t('helpCenter.adminLegal.placeholdersText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminLegal.generatedTitle')">
      <p>{{ t('helpCenter.adminLegal.generatedText') }}</p>
    </HelpSection>

    <!-- Dummy: Legal document editor -->
    <HelpSection :title="t('helpCenter.adminLegal.exampleTitle')">
      <NeutralContainer class="space-y-4">
        <SectionHeader>{{ t('adminSettings.legal.title') }}</SectionHeader>

        <!-- Tab navigation -->
        <div class="flex gap-2 flex-wrap">
          <SecondaryButton class="ring-2 ring-primary">
            {{ t('adminSettings.legal.privacy') }}
          </SecondaryButton>
          <SecondaryButton>
            {{ t('adminSettings.legal.tos') }}
          </SecondaryButton>
          <SecondaryButton>
            {{ t('adminSettings.legal.consent') }}
          </SecondaryButton>
          <SecondaryButton>
            {{ t('adminSettings.legal.imprint') }}
          </SecondaryButton>
        </div>

        <!-- Version display -->
        <div class="text-xs text-(--text-muted)">
          {{ t('adminSettings.legal.version') }}: 3
        </div>

        <!-- Markdown editor placeholder -->
        <div class="rounded-lg border border-[var(--border)] p-4 min-h-32 text-sm text-(--text-muted)">
          {{ t('helpCenter.adminLegal.editorPlaceholder') }}
        </div>

        <!-- Save button -->
        <div class="flex justify-end">
          <PrimaryButton>{{ t('adminSettings.legal.save') }}</PrimaryButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminLegal.tip') }}</HelpTip>
  </HelpArticle>
</template>
