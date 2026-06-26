/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Alert from '@/components/feedback/Alert.vue'
import DummyBoardCard from '@/views/helpcenter/stationview/boards/federatedboardshelp/DummyBoardCard.vue'
import DummyStationGroup from '@/views/helpcenter/stationview/boards/federatedboardshelp/DummyStationGroup.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('boards.federatedBoards') }}</SectionHeader>
    <MutedText size="sm">{{ t('boards.federatedBoardsDesc') }}</MutedText>
    <Alert variant="info">{{ t('boards.bookmarkHint') }}</Alert>

    <DummyStationGroup>
      <template #title>{{ t('helpCenter.federatedBoards.dummyStation1') }}</template>
      <DummyBoardCard
        :name="t('helpCenter.federatedBoards.dummyBoard1')"
        :board-key="t('helpCenter.federatedBoards.dummyBoard1Key')"
        :description="t('helpCenter.federatedBoards.dummyBoard1Desc')" />
      <DummyBoardCard
        :name="t('helpCenter.federatedBoards.dummyBoard2')"
        :board-key="t('helpCenter.federatedBoards.dummyBoard2Key')"
        :description="t('helpCenter.federatedBoards.dummyBoard2Desc')"
        full-access
        bookmarked />
    </DummyStationGroup>

    <DummyStationGroup>
      <template #title>{{ t('helpCenter.federatedBoards.dummyStation2') }}</template>
      <DummyBoardCard
        :name="t('helpCenter.federatedBoards.dummyBoard3')"
        :board-key="t('helpCenter.federatedBoards.dummyBoard3Key')"
        :description="t('helpCenter.federatedBoards.dummyBoard3Desc')" />
    </DummyStationGroup>
  </NeutralContainer>
</template>
