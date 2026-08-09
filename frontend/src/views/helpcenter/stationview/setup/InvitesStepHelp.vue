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
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import WizardFrame from './setuphelp/WizardFrame.vue'

const {t} = useI18n()

const ROWS = [
  {firstName: 'Lena', lastName: 'Hoffmann', email: 'lena.hoffmann@example.org', userType: 'MEMBER'},
  {firstName: 'Jonas', lastName: 'Weber', email: 'jonas.weber@example.org', userType: 'TEAM'},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.setupInvites.title')" :subtitle="t('helpCenter.setupInvites.subtitle')">
    <HelpSection :title="t('helpCenter.setupInvites.whatIs')">
      <p>{{ t('helpCenter.setupInvites.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupInvites.howTo')">
      <p>{{ t('helpCenter.setupInvites.howToStep1') }}</p>
      <p>{{ t('helpCenter.setupInvites.howToStep2') }}</p>
      <p>{{ t('helpCenter.setupInvites.howToStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupInvites.tabsTitle')">
      <p>{{ t('helpCenter.setupInvites.tabsRich') }}</p>
      <p>{{ t('helpCenter.setupInvites.tabsBulk') }}</p>
      <p>{{ t('helpCenter.setupInvites.tabsCsv') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupInvites.exampleTitle')">
      <p>{{ t('helpCenter.setupInvites.exampleText') }}</p>
      <WizardFrame step-id="invites" skippable>
        <div class="flex gap-2 text-sm">
          <SelectionToggleButton :selected="true">{{ t('setup.steps.invites.tabRich') }}</SelectionToggleButton>
          <SelectionToggleButton :selected="false">{{ t('setup.steps.invites.tabBulk') }}</SelectionToggleButton>
          <SelectionToggleButton :selected="false">{{ t('setup.steps.invites.tabCsv') }}</SelectionToggleButton>
        </div>
        <div class="space-y-4">
          <div v-for="row in ROWS" :key="row.email" class="border border-(--border) rounded p-3 space-y-2">
            <div class="flex flex-wrap gap-2">
              <TextInput :model-value="row.firstName" class="flex-1"/>
              <TextInput :model-value="row.lastName" class="flex-1"/>
              <TextInput :model-value="row.email" class="flex-1"/>
              <DeleteButton :title="t('setup.actions.removeRow')"/>
            </div>
            <div class="flex flex-wrap gap-2">
              <label class="block text-xs flex-1">
                {{ t('setup.steps.invites.userType') }}
                <SelectInput :model-value="row.userType">
                  <option :value="row.userType">{{ row.userType }}</option>
                </SelectInput>
              </label>
              <label class="block text-xs flex-1">
                {{ t('setup.steps.invites.group') }}
                <SelectInput model-value="1">
                  <option value="1">Jugendgruppe</option>
                </SelectInput>
              </label>
            </div>
          </div>
          <SecondaryButton>{{ t('setup.actions.addRow') }}</SecondaryButton>
        </div>
      </WizardFrame>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.setupInvites.tip') }}</HelpTip>
  </HelpArticle>
</template>
