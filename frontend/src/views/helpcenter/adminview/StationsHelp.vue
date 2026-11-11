/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import type {AdminStationSummary} from '@/api/generated/schema'
import {useDataTable} from '@/composables/useDataTable'
import StationsGrid from '@/views/adminview/adminstationsview/StationsGrid.vue'
import StationsTable from '@/views/adminview/adminstationsview/StationsTable.vue'
import StationListControls from '@/views/adminview/adminstationsview/StationListControls.vue'
import {stationColumns} from '@/views/adminview/adminstationsview/stationColumns'
import {StationFilters, StationViews} from '@/views/adminview/adminstationsview/stationList'
import {SAMPLE_STATIONS} from './stationshelp/sampleStations'

const {t} = useI18n()

const table = useDataTable<AdminStationSummary>({
  id: 'help-admin-stations',
  perStation: false,
  rows: SAMPLE_STATIONS,
  columns: computed(() => stationColumns(t)),
  rowKey: station => station.id,
})
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminStations.title')" :subtitle="t('helpCenter.adminStations.subtitle')">
    <HelpSection :title="t('helpCenter.adminStations.whatIs')">
      <p>{{ t('helpCenter.adminStations.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStations.listTitle')">
      <p>{{ t('helpCenter.adminStations.listText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStations.statesTitle')">
      <p>{{ t('helpCenter.adminStations.statesText') }}</p>
      <p>{{ t('helpCenter.adminStations.statesWaitingText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStations.filterTitle')">
      <p>{{ t('helpCenter.adminStations.filterText') }}</p>
      <p>{{ t('helpCenter.adminStations.viewText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStations.tilesTitle')">
      <div inert class="space-y-4">
        <StationListControls :filter="StationFilters.ALL" :view="StationViews.TILES"/>
        <StationsGrid :stations="[...SAMPLE_STATIONS]"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStations.tableTitle')">
      <div inert class="space-y-4">
        <StationListControls :filter="StationFilters.ALL" :view="StationViews.TABLE">
          <TableColumnPicker :table="table"/>
        </StationListControls>
        <ButtonRow>
          <PrimaryButton :icon="['fas', 'plus']">{{ t('adminStations.create') }}</PrimaryButton>
          <SecondaryButton :icon="['fas', 'upload']">{{ t('adminStations.importStation') }}</SecondaryButton>
        </ButtonRow>
        <StationsTable :table="table"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStations.remindTitle')">
      <p>{{ t('helpCenter.adminStations.remindText') }}</p>
      <p>{{ t('helpCenter.adminStations.remindAfterText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStations.autoTitle')">
      <p>{{ t('helpCenter.adminStations.autoText') }}</p>
      <p>{{ t('helpCenter.adminStations.autoSkipText') }}</p>
      <p>{{ t('helpCenter.adminStations.autoResetText') }}</p>
      <p>{{ t('helpCenter.adminStations.movedText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStations.createTitle')">
      <p>{{ t('helpCenter.adminStations.createText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStations.editTitle')">
      <p>{{ t('helpCenter.adminStations.editText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStations.importTitle')">
      <p>{{ t('helpCenter.adminStations.importText') }}</p>
      <NeutralContainer class="space-y-4">
        <p class="text-sm text-(--text-muted)">{{ t('adminStations.importHint') }}</p>
        <div class="space-y-1">
          <FieldLabel>{{ t('adminStations.importToken') }}</FieldLabel>
          <TextInput :model-value="''" :placeholder="t('adminStations.importTokenPlaceholder')" />
        </div>
        <ButtonRow pair align="end">
          <SecondaryButton>{{ t('adminStations.cancel') }}</SecondaryButton>
          <PrimaryButton>{{ t('adminStations.importStart') }}</PrimaryButton>
        </ButtonRow>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStations.deleteTitle')">
      <p>{{ t('helpCenter.adminStations.deleteText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminStations.tip') }}</HelpTip>
  </HelpArticle>
</template>
