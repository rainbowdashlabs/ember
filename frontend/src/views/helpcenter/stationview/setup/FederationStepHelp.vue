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
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import WizardFrame from './setuphelp/WizardFrame.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.setupFederation.title')" :subtitle="t('helpCenter.setupFederation.subtitle')">
    <HelpSection :title="t('helpCenter.setupFederation.whatIs')">
      <p>{{ t('helpCenter.setupFederation.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupFederation.howTo')">
      <p>{{ t('helpCenter.setupFederation.howToStep1') }}</p>
      <p>{{ t('helpCenter.setupFederation.howToStep2') }}</p>
      <p>{{ t('helpCenter.setupFederation.howToStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupFederation.exampleTitle')">
      <p>{{ t('helpCenter.setupFederation.exampleText') }}</p>
      <WizardFrame step-id="federation" skippable>
        <p class="text-sm text-(--text-muted)">{{ t('setup.steps.federation.consequence') }}</p>
        <div class="space-y-3">
          <label class="block text-sm">
            {{ t('setup.steps.federation.visibility') }}
            <SelectInput model-value="PUBLIC">
              <option value="PUBLIC">{{ t('setup.steps.federation.visibilityPublic') }}</option>
              <option value="NONE">{{ t('setup.steps.federation.visibilityNone') }}</option>
            </SelectInput>
          </label>
          <label class="block text-sm">
            {{ t('setup.steps.federation.description') }}
            <TextAreaInput model-value="Freiwillige Feuerwehr Musterstadt mit Jugendfeuerwehr und Atemschutz."/>
          </label>
          <label class="flex items-center gap-2 text-sm">
            <ToggleInput :model-value="true"/>
            {{ t('setup.steps.federation.showKb') }}
          </label>
        </div>
      </WizardFrame>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupFederation.defaultTitle')">
      <p>{{ t('helpCenter.setupFederation.defaultText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.setupFederation.tip') }}</HelpTip>
  </HelpArticle>
</template>
