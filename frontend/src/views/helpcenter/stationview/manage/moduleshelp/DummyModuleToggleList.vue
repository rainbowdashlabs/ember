/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import DummyModuleToggleRow from '@/views/helpcenter/stationview/manage/moduleshelp/DummyModuleToggleRow.vue'

const {t} = useI18n()

const modules = [
  {key: 'moduleInventory', enabled: true},
  {key: 'moduleNews', enabled: true},
  {key: 'moduleEvents', enabled: true},
  {key: 'moduleAttendance', enabled: true},
  {key: 'moduleForms', enabled: true},
  {key: 'moduleLostAndFound', enabled: false},
  {key: 'moduleWaitingList', enabled: false},
  {key: 'moduleQuiz', enabled: true},
  {key: 'moduleTestProtocol', enabled: false},
  {key: 'moduleKnowledgeBase', enabled: true},
  {key: 'moduleBoards', enabled: false},
]
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('stationManage.modulesTitle') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('stationManage.modulesHint') }}</p>
    <div class="space-y-3">
      <DummyModuleToggleRow
        v-for="m in modules"
        :key="m.key"
        :label="t(`stationManage.${m.key}`)"
        :enabled="m.enabled"
      />
    </div>
  </NeutralContainer>
</template>
