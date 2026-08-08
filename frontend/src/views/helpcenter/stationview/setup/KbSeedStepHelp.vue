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
import InfoContainer from '@/components/container/InfoContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import WizardFrame from './setuphelp/WizardFrame.vue'

const {t} = useI18n()

const SAMPLE_CONTENT = '## Kontakt\n\nUnsere Wache erreichst du unter kontakt@example.org.'
</script>

<template>
  <HelpArticle :title="t('helpCenter.setupKbSeed.title')" :subtitle="t('helpCenter.setupKbSeed.subtitle')">
    <HelpSection :title="t('helpCenter.setupKbSeed.whatIs')">
      <p>{{ t('helpCenter.setupKbSeed.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupKbSeed.howTo')">
      <p>{{ t('helpCenter.setupKbSeed.howToStep1') }}</p>
      <p>{{ t('helpCenter.setupKbSeed.howToStep2') }}</p>
      <p>{{ t('helpCenter.setupKbSeed.howToStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupKbSeed.exampleTitle')">
      <p>{{ t('helpCenter.setupKbSeed.exampleText') }}</p>
      <WizardFrame step-id="kb-seed" skippable :save-label="t('setup.steps.kb-seed.createPage')">
        <InfoContainer class="space-y-1">
          <p class="font-medium text-sm">{{ t('setup.steps.kb-seed.aboutTitle') }}</p>
          <p class="text-sm">{{ t('setup.steps.kb-seed.aboutBody') }}</p>
        </InfoContainer>
        <div class="space-y-1">
          <FieldLabel>{{ t('setup.steps.kb-seed.pageName') }}</FieldLabel>
          <TextInput model-value="Über uns" :placeholder="t('setup.steps.kb-seed.pagePlaceholder')"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('setup.steps.kb-seed.pageDescription') }}</FieldLabel>
          <TextAreaInput model-value="Wer wir sind und wo du uns findest."/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('setup.steps.kb-seed.pageContent') }}</FieldLabel>
          <MarkdownEditor :model-value="SAMPLE_CONTENT"/>
        </div>
      </WizardFrame>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupKbSeed.editorTitle')">
      <p>{{ t('helpCenter.setupKbSeed.editorText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.setupKbSeed.tip') }}</HelpTip>
  </HelpArticle>
</template>
