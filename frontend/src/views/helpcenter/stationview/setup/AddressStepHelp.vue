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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import WizardFrame from './setuphelp/WizardFrame.vue'

const {t} = useI18n()

const noop = () => undefined
</script>

<template>
  <HelpArticle :title="t('helpCenter.setupAddress.title')" :subtitle="t('helpCenter.setupAddress.subtitle')">
    <HelpSection :title="t('helpCenter.setupAddress.whatIs')">
      <p>{{ t('helpCenter.setupAddress.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupAddress.howTo')">
      <p>{{ t('helpCenter.setupAddress.howToStep1') }}</p>
      <p>{{ t('helpCenter.setupAddress.howToStep2') }}</p>
      <p>{{ t('helpCenter.setupAddress.howToStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupAddress.exampleTitle')">
      <p>{{ t('helpCenter.setupAddress.exampleText') }}</p>
      <WizardFrame step-id="address">
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('geolocation.sectionTitle') }}</SectionHeader>
          <p class="text-sm text-(--text-muted)">{{ t('geolocation.sectionSubtitle') }}</p>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div class="space-y-1 md:col-span-2">
              <FieldLabel>{{ t('geolocation.addressLine') }}</FieldLabel>
              <TextInput model-value="Hauptstraße 12"/>
            </div>
            <div class="space-y-1">
              <FieldLabel>{{ t('geolocation.postalCode') }}</FieldLabel>
              <TextInput model-value="38100"/>
            </div>
            <div class="space-y-1">
              <FieldLabel>{{ t('geolocation.city') }}</FieldLabel>
              <TextInput model-value="Braunschweig"/>
            </div>
            <div class="space-y-1 md:col-span-2">
              <FieldLabel>{{ t('geolocation.country') }}</FieldLabel>
              <SelectInput model-value="DE">
                <option value="DE">Deutschland (DE)</option>
              </SelectInput>
            </div>
          </div>
          <p class="text-sm text-(--text-muted)">{{ t('geolocation.pickHint') }}</p>
          <div class="flex flex-wrap gap-2 justify-end pt-2 border-t border-(--border)">
            <DeleteButton>{{ t('geolocation.clear') }}</DeleteButton>
            <SaveButton :action="noop"/>
          </div>
        </NeutralContainer>
      </WizardFrame>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupAddress.whyTitle')">
      <p>{{ t('helpCenter.setupAddress.whyText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.setupAddress.tip') }}</HelpTip>
  </HelpArticle>
</template>
