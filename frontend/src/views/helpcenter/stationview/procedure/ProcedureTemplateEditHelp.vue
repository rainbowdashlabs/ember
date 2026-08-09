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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import TemplateItemCard from '@/views/stationview/procedure/proceduretemplateeditview/TemplateItemCard.vue'
import type {ProcedureTemplateItem} from '@/api/procedures'

const {t} = useI18n()

const ITEMS: ProcedureTemplateItem[] = [
  {
    id: 1,
    templateId: 1,
    title: 'Aufnahmeantrag unterschreiben lassen',
    description: 'Von der Person und bei Minderjährigen von den Erziehungsberechtigten.',
    isPublic: true,
    userAssigned: false,
    position: 0,
  },
  {
    id: 2,
    templateId: 1,
    title: 'Einsatzkleidung ausgeben',
    description: 'Größen im Inventar prüfen und zuweisen.',
    isPublic: true,
    userAssigned: true,
    position: 1,
  },
]

const getItemById = (id: number) => ITEMS.find(item => item.id === id)
</script>

<template>
  <HelpArticle :title="t('helpCenter.procedureTemplateEdit.title')"
               :subtitle="t('helpCenter.procedureTemplateEdit.subtitle')">
    <HelpSection :title="t('helpCenter.procedureTemplateEdit.whatIs')">
      <p>{{ t('helpCenter.procedureTemplateEdit.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.procedureTemplateEdit.howTo')">
      <p>{{ t('helpCenter.procedureTemplateEdit.howToStep1') }}</p>
      <p>{{ t('helpCenter.procedureTemplateEdit.howToStep2') }}</p>
      <p>{{ t('helpCenter.procedureTemplateEdit.howToStep3') }}</p>
      <div class="flex items-start justify-end mb-4 gap-2">
        <SecondaryButton :icon="['fas', 'pen']">{{ t('common.edit') }}</SecondaryButton>
        <SecondaryButton :icon="['fas', 'arrow-left']">{{ t('common.back') }}</SecondaryButton>
      </div>
      <div class="flex items-center justify-between mb-3">
        <SubHeader>{{ t('procedures.items') }}</SubHeader>
        <PrimaryButton :icon="['fas', 'plus']">{{ t('procedures.addItem') }}</PrimaryButton>
      </div>
      <div class="space-y-2">
        <TemplateItemCard :item="ITEMS[0]!" :index="0" :can-manage="true"
                          :dependencies="[]" :get-item-by-id="getItemById"/>
        <TemplateItemCard :item="ITEMS[1]!" :index="1" :can-manage="true"
                          :dependencies="[1]" :get-item-by-id="getItemById"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.procedureTemplateEdit.exampleTitle')">
      <p>{{ t('helpCenter.procedureTemplateEdit.exampleText') }}</p>
      <NeutralContainer class="space-y-3">
        <SubHeader>{{ t('procedures.addItem') }}</SubHeader>
        <TextInput model-value="Schlüssel übergeben" :placeholder="t('procedures.itemTitle')"/>
        <TextAreaInput model-value="" :placeholder="t('procedures.itemDescription')"/>
        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
            <FieldLabel>{{ t('procedures.itemPublic') }}</FieldLabel>
            <ToggleInput :model-value="true"/>
          </div>
          <div class="flex items-center gap-2">
            <FieldLabel>{{ t('procedures.itemUserAssigned') }}</FieldLabel>
            <ToggleInput :model-value="false"/>
          </div>
        </div>
        <div class="flex gap-2 justify-end">
          <PrimaryButton>{{ t('common.save') }}</PrimaryButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.procedureTemplateEdit.dependencyTitle')">
      <p>{{ t('helpCenter.procedureTemplateEdit.dependencyText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.procedureTemplateEdit.togglesTitle')">
      <p>{{ t('helpCenter.procedureTemplateEdit.togglesText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.procedureTemplateEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
