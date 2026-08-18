/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import PitchAppFrame from './PitchAppFrame.vue'
import PitchScreenHeadline from './PitchScreenHeadline.vue'
import PitchToolbar from './PitchToolbar.vue'
import PitchFilterBar from './PitchFilterBar.vue'
import TrainingCatalogSelect from '@/views/stationview/quiz/trainingview/TrainingCatalogSelect.vue'
import KbItemGrid from '@/views/stationview/knowledge/knowledgebaseview/KbItemGrid.vue'
import CommentThread from '@/components/comment/CommentThread.vue'
import InventoryStatsPanel from '@/views/stationview/inventory/detailview/InventoryStatsPanel.vue'
import InventorySection from '@/views/stationview/inventory/checkmemberview/InventorySection.vue'
import RapidCheckMode from '@/views/stationview/inventory/checkmemberview/RapidCheckMode.vue'
import PitchStorageTree from './PitchStorageTree.vue'
import PitchBoard from './PitchBoard.vue'
import PitchAttendanceList from './PitchAttendanceList.vue'
import PitchNotifications from './PitchNotifications.vue'
import PitchTrust from './PitchTrust.vue'
import PitchFederation from './PitchFederation.vue'
import PitchTransfer from './PitchTransfer.vue'
import PitchPages from './PitchPages.vue'
import PitchWaitlist from './PitchWaitlist.vue'
import PitchFormList from './PitchFormList.vue'
import PitchFormFill from './PitchFormFill.vue'
import PitchFormAnalytics from './PitchFormAnalytics.vue'
import PitchNewsList from './PitchNewsList.vue'
import PitchNewsSettings from './PitchNewsSettings.vue'
import PitchProcedure from './PitchProcedure.vue'
import PitchProcedureTemplate from './PitchProcedureTemplate.vue'
import PitchCheckMode from './PitchCheckMode.vue'
import GradingSectionPanel from '@/views/stationview/protocol/gradingview/GradingSectionPanel.vue'
import EvaluationTable from '@/views/stationview/protocol/evaluationview/EvaluationTable.vue'
import PitchPanelGrid from './PitchPanelGrid.vue'
import PitchTable from './PitchTable.vue'
import PitchRowList from './PitchRowList.vue'
import type {PitchScreen} from './pitchTypes'

defineProps<{
  screen: PitchScreen
}>()
</script>

<template>
  <PitchAppFrame
      :station="screen.station ?? 'Musterstadt'"
      :title="screen.title" :subtitle="screen.subtitle">
    <PitchToolbar v-if="screen.toolbar" :toolbar="screen.toolbar"/>
    <PitchFilterBar v-if="screen.filters" :filters="screen.filters"/>
    <PitchScreenHeadline :screen="screen"/>

    <InventoryStatsPanel v-if="screen.stats" v-bind="screen.stats"/>
    <PitchBoard v-if="screen.board" :board="screen.board"/>
    <CommentThread v-if="screen.comments" :comments="screen.comments" :members="[]" readonly/>
    <TrainingCatalogSelect v-if="screen.catalogs" :catalogs="screen.catalogs.items"
                           :selected-catalog-ids="screen.catalogs.selected"/>
    <InventorySection v-if="screen.inventoryCheck" v-bind="screen.inventoryCheck"/>
    <RapidCheckMode v-if="screen.rapidCheck" v-bind="screen.rapidCheck"/>
    <PitchStorageTree v-if="screen.storage" :storage="screen.storage"/>
    <PitchCheckMode v-if="screen.check" :check="screen.check"/>
    <PitchAttendanceList v-if="screen.attendance" :attendance="screen.attendance"/>
    <PitchNotifications v-if="screen.notifications" :notifications="screen.notifications"/>
    <PitchTrust v-if="screen.trust" :trust="screen.trust"/>
    <PitchFederation v-if="screen.federation" :federation="screen.federation"/>
    <PitchTransfer v-if="screen.transfer" :transfer="screen.transfer"/>
    <PitchPages v-if="screen.pages" :pages="screen.pages.data" :section="screen.pages.section"/>
    <PitchWaitlist v-if="screen.waitlist" :waitlist="screen.waitlist.data" :section="screen.waitlist.section"/>
    <PitchFormList v-if="screen.forms" :forms="screen.forms"/>
    <PitchFormFill v-if="screen.form" :form="screen.form"/>
    <PitchFormAnalytics v-if="screen.formAnalytics" :analytics="screen.formAnalytics"/>
    <PitchNewsList v-if="screen.news" :items="screen.news"/>
    <PitchNewsSettings v-if="screen.newsSettings" :settings="screen.newsSettings"/>
    <PitchProcedure v-if="screen.procedure" :procedure="screen.procedure"/>
    <PitchProcedureTemplate v-if="screen.procedureTemplate" :template="screen.procedureTemplate"/>
    <GradingSectionPanel v-if="screen.grading" v-bind="screen.grading"/>
    <EvaluationTable v-if="screen.evaluation" v-bind="screen.evaluation"/>
    <PitchPanelGrid v-if="screen.panels" :panels="screen.panels"/>
    <KbItemGrid v-if="screen.tiles" :items="screen.tiles"/>
    <PitchTable v-if="screen.table" :table="screen.table"/>
    <PitchRowList v-if="screen.rows" :rows="screen.rows"/>

    <p v-if="screen.footer" class="text-xs text-(--text-muted)">{{ screen.footer }}</p>
  </PitchAppFrame>
</template>
