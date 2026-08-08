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
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import ProcedureItemRow from '@/views/stationview/procedure/proceduredetailview/ProcedureItemRow.vue'
import type {ProcedureItem} from '@/api/procedures'
import {StationPermission} from '@/api/types'

const {t} = useI18n()

const DONE_ITEM: ProcedureItem = {
  id: 1,
  procedureId: 1,
  title: 'Aufnahmeantrag unterschreiben lassen',
  description: 'Von der Person und bei Minderjährigen von den Erziehungsberechtigten.',
  note: 'Antrag liegt im Ordner „Aufnahmen".',
  isPublic: true,
  userAssigned: false,
  position: 0,
  checked: true,
  checkedAt: '2026-05-02T17:40:00Z',
  checkedBy: 1,
}

const LOCKED_ITEM: ProcedureItem = {
  id: 2,
  procedureId: 1,
  title: 'Einsatzkleidung ausgeben',
  description: 'Größen im Inventar prüfen und zuweisen.',
  note: null,
  isPublic: true,
  userAssigned: true,
  position: 1,
  checked: false,
  checkedAt: null,
  checkedBy: null,
}
</script>

<template>
  <HelpArticle :title="t('helpCenter.procedureDetail.title')" :subtitle="t('helpCenter.procedureDetail.subtitle')">
    <HelpSection :title="t('helpCenter.procedureDetail.whatIs')">
      <p>{{ t('helpCenter.procedureDetail.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.procedureDetail.howTo')">
      <p>{{ t('helpCenter.procedureDetail.howToStep1') }}</p>
      <p>{{ t('helpCenter.procedureDetail.howToStep2') }}</p>
      <p>{{ t('helpCenter.procedureDetail.howToStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.procedureDetail.exampleTitle')">
      <p>{{ t('helpCenter.procedureDetail.exampleText') }}</p>
      <div class="flex items-start justify-between mb-4 gap-4">
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2 mb-1 flex-wrap">
            <PrimaryBadge>{{ t('procedures.open') }}</PrimaryBadge>
            <ErrorBadge>{{ t('procedures.overdue') }}</ErrorBadge>
          </div>
          <p class="text-(--text-muted) text-sm">Alle Schritte bis zum ersten Übungsabend.</p>
        </div>
        <div class="flex gap-2 shrink-0">
          <SecondaryButton :icon="['fas', 'pen']">{{ t('common.edit') }}</SecondaryButton>
          <SuccessButton :icon="['fas', 'check']">{{ t('procedures.resolve') }}</SuccessButton>
        </div>
      </div>
      <NeutralContainer class="mb-4">
        <div class="flex items-center gap-3">
          <span class="text-sm font-medium">{{ t('procedures.progress') }}</span>
          <div class="flex-1 h-2 bg-(--bg-accent) rounded-full overflow-hidden">
            <div class="h-full bg-(--success) rounded-full" style="width: 50%"/>
          </div>
          <span class="text-sm text-(--text-muted)">1/2</span>
        </div>
      </NeutralContainer>
      <SubHeader class="mb-3">{{ t('procedures.items') }}</SubHeader>
      <div class="space-y-2">
        <ProcedureItemRow :item="DONE_ITEM" :can-edit="true" :can-check="true"
                          :dependency-met="true" :dependency-names="[]"/>
        <ProcedureItemRow :item="LOCKED_ITEM" :can-edit="true" :can-check="false"
                          :dependency-met="false" :dependency-names="[DONE_ITEM.title]"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.procedureDetail.lockedTitle')">
      <p>{{ t('helpCenter.procedureDetail.lockedText') }}</p>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.PROCEDURE_EDIT]"
                         :label="t('helpCenter.permissionLabel.procedureEdit')">
      <HelpSection :title="t('helpCenter.procedureDetail.resolveTitle')">
        <p>{{ t('helpCenter.procedureDetail.resolveText') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpTip>{{ t('helpCenter.procedureDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
