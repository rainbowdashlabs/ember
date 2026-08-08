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
import HelpPermissionGuard from '@/components/helpcenter/HelpPermissionGuard.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import {StationPermission} from '@/api/types'

const {t} = useI18n()

const TEMPLATES = [
  {name: 'Aufnahme neues Mitglied', description: 'Vom Antrag bis zur ersten Übung.', archived: false},
  {name: 'Ausmusterung Fahrzeug', description: 'Nicht mehr in Gebrauch.', archived: true},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.procedureTemplates.title')"
               :subtitle="t('helpCenter.procedureTemplates.subtitle')">
    <HelpSection :title="t('helpCenter.procedureTemplates.whatIs')">
      <p>{{ t('helpCenter.procedureTemplates.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.procedureTemplates.howTo')">
      <p>{{ t('helpCenter.procedureTemplates.howToStep1') }}</p>
      <p>{{ t('helpCenter.procedureTemplates.howToStep2') }}</p>
      <p>{{ t('helpCenter.procedureTemplates.howToStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.procedureTemplates.exampleTitle')">
      <p>{{ t('helpCenter.procedureTemplates.exampleText') }}</p>
      <div class="flex items-center justify-end mb-4">
        <PrimaryButton :icon="['fas', 'plus']">{{ t('procedures.createTemplate') }}</PrimaryButton>
      </div>
      <div class="flex items-center gap-2 mb-4">
        <SelectionToggleButton :selected="true">{{ t('procedures.showArchived') }}</SelectionToggleButton>
      </div>
      <div class="space-y-2">
        <NeutralContainer v-for="tpl in TEMPLATES" :key="tpl.name" class="flex items-center gap-3"
                          :class="{'opacity-60': tpl.archived}">
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2">
              <span class="font-medium">{{ tpl.name }}</span>
              <InfoBadge v-if="tpl.archived">{{ t('boards.archived') }}</InfoBadge>
            </div>
            <div class="text-sm text-(--text-muted) truncate">{{ tpl.description }}</div>
          </div>
          <div class="flex gap-1">
            <EditButton :label="t('common.edit')"/>
            <IconButton v-if="!tpl.archived" :icon="['fas', 'box-archive']"
                        :label="t('procedures.archiveTemplate')"/>
          </div>
        </NeutralContainer>
      </div>
      <NeutralContainer class="space-y-3 mt-4">
        <SubHeader>{{ t('procedures.createTemplate') }}</SubHeader>
        <TextInput model-value="" :placeholder="t('procedures.templateName')"/>
        <TextAreaInput model-value="" :placeholder="t('procedures.templateDescription')"/>
        <div class="flex gap-2 justify-end">
          <PrimaryButton>{{ t('procedures.createTemplate') }}</PrimaryButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.PROCEDURE_MANAGER]"
                         :label="t('helpCenter.permissionLabel.procedureManager')">
      <HelpSection :title="t('helpCenter.procedureTemplates.archiveTitle')">
        <p>{{ t('helpCenter.procedureTemplates.archiveText') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpTip>{{ t('helpCenter.procedureTemplates.tip') }}</HelpTip>
  </HelpArticle>
</template>
