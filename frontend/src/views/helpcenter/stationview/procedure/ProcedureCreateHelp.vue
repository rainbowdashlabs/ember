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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TemplateSelectorSection from '@/views/stationview/procedure/procedurecreateview/TemplateSelectorSection.vue'
import BasicInfoSection from '@/views/stationview/procedure/procedurecreateview/BasicInfoSection.vue'
import AssigneesSection from '@/views/stationview/procedure/procedurecreateview/AssigneesSection.vue'
import ItemsSection from '@/views/stationview/procedure/procedurecreateview/ItemsSection.vue'
import type {EditableItem} from '@/views/stationview/procedure/procedurecreateview/types'
import type {ProcedureTemplate} from '@/api/procedures'
import type {MemberCompletion} from '@/api/stationMembers'

const {t} = useI18n()

const TEMPLATES: ProcedureTemplate[] = [
  {
    id: 1,
    stationId: 1,
    name: 'Aufnahme neues Mitglied',
    description: null,
    archived: false,
    createdBy: 1,
    createdAt: '2026-01-12T10:00:00Z',
  },
]

const MEMBERS: MemberCompletion[] = [
  {id: 1, name: 'Sabine Krüger', stationUid: 'demo', memberUid: 'm-1'},
  {id: 2, name: 'Jonas Weber', stationUid: 'demo', memberUid: 'm-2'},
]

const ITEMS: EditableItem[] = [
  {
    tempId: 1,
    title: 'Aufnahmeantrag unterschreiben lassen',
    description: 'Von der Person und bei Minderjährigen von den Erziehungsberechtigten.',
    isPublic: true,
    userAssigned: false,
    position: 0,
    dependsOn: [],
  },
  {
    tempId: 2,
    title: 'Einsatzkleidung ausgeben',
    description: 'Größen im Inventar prüfen und zuweisen.',
    isPublic: true,
    userAssigned: true,
    position: 1,
    dependsOn: [1],
  },
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.procedureCreate.title')" :subtitle="t('helpCenter.procedureCreate.subtitle')">
    <HelpSection :title="t('helpCenter.procedureCreate.whatIs')">
      <p>{{ t('helpCenter.procedureCreate.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.procedureCreate.howTo')">
      <p>{{ t('helpCenter.procedureCreate.howToStep1') }}</p>
      <p>{{ t('helpCenter.procedureCreate.howToStep2') }}</p>
      <p>{{ t('helpCenter.procedureCreate.howToStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.procedureCreate.exampleTitle')">
      <p>{{ t('helpCenter.procedureCreate.exampleText') }}</p>
      <div class="space-y-6">
        <TemplateSelectorSection :templates="TEMPLATES" :selected-template-id="1"/>
        <BasicInfoSection name="Aufnahme Lena Hoffmann"
                          description="Alle Schritte bis zum ersten Übungsabend."
                          due-at="2026-06-12" :is-public="true"/>
        <AssigneesSection assignee-picker-value="" :members="MEMBERS"
                          :selected-assignees="[MEMBERS[0]!]" :selected-assignee-ids="[1]"/>
        <ItemsSection :items="ITEMS"/>
        <div class="flex justify-end gap-2">
          <SecondaryButton>{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton>{{ t('procedures.createProcedure') }}</PrimaryButton>
        </div>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.procedureCreate.itemsTitle')">
      <p>{{ t('helpCenter.procedureCreate.itemsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.procedureCreate.fromSignupsTitle')">
      <p>{{ t('helpCenter.procedureCreate.fromSignupsText') }}</p>
      <p>{{ t('helpCenter.procedureCreate.fromSignupsTemplateText') }}</p>
      <p>{{ t('helpCenter.procedureCreate.fromSignupsSharedText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.procedureCreate.editTitle')">
      <p>{{ t('helpCenter.procedureCreate.editText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.procedureCreate.tip') }}</HelpTip>
  </HelpArticle>
</template>
