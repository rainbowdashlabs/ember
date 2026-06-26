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
import HelpList from '@/components/helpcenter/HelpList.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import DummyMainArea from './ticketdetailhelp/DummyMainArea.vue'
import DummySidebar from './ticketdetailhelp/DummySidebar.vue'
import DummyAddMenu from './ticketdetailhelp/DummyAddMenu.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.ticketDetail.title')" :subtitle="t('helpCenter.ticketDetail.subtitle')">
    <HelpSection :title="t('helpCenter.ticketDetail.whatIs')">
      <p>{{ t('helpCenter.ticketDetail.whatIsText') }}</p>
      <p>{{ t('helpCenter.ticketDetail.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.headerTitle')">
      <p>{{ t('helpCenter.ticketDetail.headerText') }}</p>
      <NeutralContainer>
        <div class="flex items-center gap-3 mb-4">
          <IconButton :icon="['fas', 'chevron-left']" label="Back"/>
          <span class="font-mono text-(--text-muted)">JF-42</span>
          <div class="ml-auto flex items-center gap-1">
            <IconButton :icon="['fas', 'eye']" :label="t('boards.watch')" class="text-(--text-muted)"/>
            <div class="relative">
              <IconButton :icon="['fas', 'ellipsis']" label="Menu" class="text-(--text-muted)"/>
            </div>
          </div>
        </div>
        <div class="text-lg font-semibold rounded-theme px-2 py-1 cursor-pointer hover:bg-(--bg-accent)">
          {{ t('helpCenter.ticketDetail.dummyTitle') }}
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.clickToEditTitle')">
      <p>{{ t('helpCenter.ticketDetail.clickToEditText') }}</p>
      <p>{{ t('helpCenter.ticketDetail.clickToEditText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.layoutTitle')">
      <p>{{ t('helpCenter.ticketDetail.layoutText') }}</p>
      <NeutralContainer>
        <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <DummyMainArea/>
          <DummySidebar/>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.addMenuTitle')">
      <p>{{ t('helpCenter.ticketDetail.addMenuText') }}</p>
      <DummyAddMenu/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.sidebarTitle')">
      <p>{{ t('helpCenter.ticketDetail.sidebarText') }}</p>
      <p>{{ t('helpCenter.ticketDetail.sidebarLaneText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.linkTypesTitle')">
      <p>{{ t('helpCenter.ticketDetail.linkTypesText') }}</p>
      <HelpList>
        <li><strong>{{ t('boards.linkRelatesTo') }}:</strong> {{ t('helpCenter.ticketDetail.linkRelatesDesc') }}</li>
        <li><strong>{{ t('boards.linkBlocks') }} / {{ t('boards.linkBlockedBy') }}:</strong> {{ t('helpCenter.ticketDetail.linkBlocksDesc') }}</li>
        <li><strong>{{ t('boards.linkCauses') }} / {{ t('boards.linkCausedBy') }}:</strong> {{ t('helpCenter.ticketDetail.linkCausesDesc') }}</li>
      </HelpList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.activityTitle')">
      <p>{{ t('helpCenter.ticketDetail.activityText') }}</p>
      <HelpList>
        <li><strong>{{ t('helpCenter.ticketDetail.tabComments') }}:</strong> {{ t('helpCenter.ticketDetail.tabCommentsDesc') }}</li>
        <li><strong>{{ t('helpCenter.ticketDetail.tabChanges') }}:</strong> {{ t('helpCenter.ticketDetail.tabChangesDesc') }}</li>
        <li><strong>{{ t('helpCenter.ticketDetail.tabAll') }}:</strong> {{ t('helpCenter.ticketDetail.tabAllDesc') }}</li>
      </HelpList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.watchTitle')">
      <p>{{ t('helpCenter.ticketDetail.watchText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.deleteTitle')">
      <p>{{ t('helpCenter.ticketDetail.deleteText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.deleteModalTitle')">
      <NeutralContainer>
        <SubHeader class="mb-4">{{ t('common.delete') }}</SubHeader>
        <p class="mb-4">{{ t('helpCenter.ticketDetail.deleteConfirm') }}</p>
        <div class="flex justify-end gap-2">
          <DeleteButton>{{ t('common.delete') }}</DeleteButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.ticketDetail.tip') }}</HelpTip>
    <HelpTip>{{ t('helpCenter.ticketDetail.tip2') }}</HelpTip>
  </HelpArticle>
</template>
