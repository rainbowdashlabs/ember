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
import FederatedBoardHeader from '@/views/stationview/federation/federatedboardview/FederatedBoardHeader.vue'
import {priorityColor, priorityIcon} from '@/util/ticketPriority'
import {StationPermission} from '@/api/types'

const {t} = useI18n()

const laneName = () => 'Offen'
</script>

<template>
  <HelpArticle :title="t('helpCenter.federatedBoardView.title')"
               :subtitle="t('helpCenter.federatedBoardView.subtitle')">
    <HelpSection :title="t('helpCenter.federatedBoardView.whatIs')">
      <p>{{ t('helpCenter.federatedBoardView.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedBoardView.howTo')">
      <p>{{ t('helpCenter.federatedBoardView.howToStep1') }}</p>
      <p>{{ t('helpCenter.federatedBoardView.howToStep2') }}</p>
      <p>{{ t('helpCenter.federatedBoardView.howToStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedBoardView.exampleTitle')">
      <p>{{ t('helpCenter.federatedBoardView.exampleText') }}</p>
      <FederatedBoardHeader :board-name="t('helpCenter.federatedBoards.dummyBoard1')"
                            :short-key="t('helpCenter.federatedBoards.dummyBoard1Key')"
                            :is-read-only="false" :is-full="true" :can-manage-boards="true"
                            search-query="" :search-results="null" :lane-name="laneName"
                            :priority-icon="priorityIcon" :priority-color="priorityColor"/>
      <div class="flex items-center gap-2 mb-4 text-sm text-(--text-muted)">
        <font-awesome-icon :icon="['fas', 'share-nodes']"/>
        <span>
          {{ t('boards.federatedFrom') }}:
          <strong class="text-(--text)">{{ t('helpCenter.federatedBoards.dummyStation1') }}</strong>
        </span>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedBoardView.accessTitle')">
      <p>{{ t('helpCenter.federatedBoardView.accessText') }}</p>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.BOARD_MANAGER]"
                         :label="t('helpCenter.permissionLabel.boardManager')">
      <HelpSection :title="t('helpCenter.federatedBoardView.overrideTitle')">
        <p>{{ t('helpCenter.federatedBoardView.overrideText') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpTip>{{ t('helpCenter.federatedBoardView.tip') }}</HelpTip>
  </HelpArticle>
</template>
