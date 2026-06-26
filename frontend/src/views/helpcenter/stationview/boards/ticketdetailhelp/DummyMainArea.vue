/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import DummyChecklist from './DummyChecklist.vue'
import DummyLinkedTickets from './DummyLinkedTickets.vue'
import DummyAttachments from './DummyAttachments.vue'
import DummyActivity from './DummyActivity.vue'

const {t} = useI18n()

const dummyTabs = [
  {key: 'comments', label: 'Kommentare (3)'},
  {key: 'transitions', label: 'Änderungen (5)'},
  {key: 'all', label: 'Alle'},
]
</script>

<template>
  <div class="lg:col-span-2 space-y-4">
    <div class="relative inline-block">
      <IconButton :icon="['fas', 'plus']" label="Add" class="text-(--text-muted)"/>
    </div>

    <div>
      <FieldLabel class="mb-1">{{ t('boards.ticketDescription') }}</FieldLabel>
      <div class="prose prose-sm max-w-none rounded-theme p-2 cursor-pointer hover:bg-(--bg-accent)">
        <p>{{ t('helpCenter.ticketDetail.dummyDescription') }}</p>
      </div>
    </div>

    <DummyChecklist/>

    <DummyLinkedTickets/>

    <div>
      <FieldLabel class="mb-1 text-sm">{{ t('boards.weblinks') }}</FieldLabel>
      <div class="flex items-center gap-2 text-sm">
        <font-awesome-icon :icon="['fas', 'globe']" class="text-(--text-muted) text-xs"/>
        <span class="text-(--accent)">{{ t('helpCenter.ticketDetail.weblinkExample') }}</span>
      </div>
    </div>

    <DummyAttachments/>

    <div>
      <FieldLabel class="mb-1 text-sm">{{ t('boards.kbLinks') }}</FieldLabel>
      <div class="flex items-center gap-2 text-sm">
        <font-awesome-icon :icon="['fas', 'book']" class="text-(--text-muted) text-xs shrink-0"/>
        <span class="text-primary">{{ t('helpCenter.ticketDetail.kbLinkExample') }}</span>
      </div>
    </div>

    <TabBar model-value="comments" :tabs="dummyTabs" class="mb-3"/>
    <NeutralContainer class="space-y-3">
      <DummyActivity/>
    </NeutralContainer>
  </div>
</template>
