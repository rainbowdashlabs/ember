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
import DeleteButton from '@/components/button/DeleteButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import {StationPermission} from '@/api/types'

const {t} = useI18n()

const PROCEDURES = [
  {name: 'Aufnahme neues Mitglied', description: 'Alle Schritte bis zum ersten Übungsabend.', due: '12.06.2026', resolved: false},
  {name: 'Wettkampf-Vorbereitung', description: 'Material, Anmeldung und Fahrgemeinschaften.', due: '30.04.2026', resolved: true},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.procedureList.title')" :subtitle="t('helpCenter.procedureList.subtitle')">
    <HelpSection :title="t('helpCenter.procedureList.whatIs')">
      <p>{{ t('helpCenter.procedureList.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.procedureList.howTo')">
      <p>{{ t('helpCenter.procedureList.howToStep1') }}</p>
      <p>{{ t('helpCenter.procedureList.howToStep2') }}</p>
      <p>{{ t('helpCenter.procedureList.howToStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.procedureList.exampleTitle')">
      <p>{{ t('helpCenter.procedureList.exampleText') }}</p>
      <div class="flex items-center justify-end mb-4">
        <PrimaryButton :icon="['fas', 'plus']">{{ t('procedures.createProcedure') }}</PrimaryButton>
      </div>
      <div class="mb-4">
        <SearchInput model-value="" :placeholder="t('procedures.search')"/>
      </div>
      <div class="flex flex-wrap items-center gap-2 mb-4">
        <SelectionToggleButton :selected="true">{{ t('procedures.open') }}</SelectionToggleButton>
        <SelectionToggleButton :selected="false">{{ t('procedures.resolved') }}</SelectionToggleButton>
        <span class="text-(--text-muted) mx-1">|</span>
        <SelectionToggleButton :selected="false">{{ t('procedures.filterMine') }}</SelectionToggleButton>
        <SelectionToggleButton :selected="true">{{ t('procedures.filterAll') }}</SelectionToggleButton>
      </div>
      <div class="space-y-2">
        <NeutralContainer v-for="p in PROCEDURES" :key="p.name" class="flex items-center gap-3">
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2">
              <span class="font-medium">{{ p.name }}</span>
              <SuccessBadge v-if="p.resolved">{{ t('procedures.resolved') }}</SuccessBadge>
              <PrimaryBadge v-else>{{ t('procedures.open') }}</PrimaryBadge>
            </div>
            <div class="text-sm text-(--text-muted) truncate">{{ p.description }}</div>
          </div>
          <div class="flex items-center gap-3 text-sm text-(--text-muted) shrink-0">
            <span class="flex items-center gap-1">
              <font-awesome-icon :icon="['fas', 'calendar']" class="w-3 h-3"/>
              {{ p.due }}
            </span>
          </div>
          <DeleteButton :label="t('common.delete')"/>
        </NeutralContainer>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.procedureList.badgeTitle')">
      <p>{{ t('helpCenter.procedureList.badgeText') }}</p>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.PROCEDURE_EDIT]"
                         :label="t('helpCenter.permissionLabel.procedureEdit')">
      <HelpSection :title="t('helpCenter.procedureList.deleteTitle')">
        <p>{{ t('helpCenter.procedureList.deleteText') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpTip>{{ t('helpCenter.procedureList.tip') }}</HelpTip>
  </HelpArticle>
</template>
