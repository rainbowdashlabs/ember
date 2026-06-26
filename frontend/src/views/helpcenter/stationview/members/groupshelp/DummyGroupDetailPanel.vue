/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import IconButton from '@/components/button/IconButton.vue'
import TabBar from '@/components/navigation/TabBar.vue'

const {t} = useI18n()

const detailTab = ref('members')
const detailTabs = [
  {key: 'members', label: t('memberGroups.tabMembers')},
  {key: 'permissions', label: t('memberGroups.tabPermissions')},
]
</script>

<template>
  <div class="space-y-4">
    <SectionHeader>Anfänger</SectionHeader>
    <TabBar :model-value="detailTab" :tabs="detailTabs"/>
    <div class="space-y-1">
      <FieldLabel class="text-(--text-muted)">{{ t('memberGroups.currentMembers') }}</FieldLabel>
      <div class="space-y-1">
        <div class="flex items-center justify-between rounded-lg px-3 py-2 bg-bg-light-accent dark:bg-bg-dark-accent">
          <span class="text-sm font-medium">Max Mustermann</span>
          <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-error hover:text-error/80" />
        </div>
        <div class="flex items-center justify-between rounded-lg px-3 py-2 bg-bg-light-accent dark:bg-bg-dark-accent">
          <span class="text-sm font-medium">Anna Schmidt</span>
          <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-error hover:text-error/80" />
        </div>
      </div>
    </div>
    <div class="space-y-1">
      <FieldLabel class="text-(--text-muted)">{{ t('memberGroups.addMembers') }}</FieldLabel>
      <div class="space-y-1">
        <div class="flex items-center justify-between rounded-lg px-3 py-2 hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent cursor-pointer">
          <span class="text-sm font-medium">Lisa Weber</span>
          <font-awesome-icon :icon="['fas', 'plus']" class="text-primary text-sm"/>
        </div>
      </div>
    </div>
  </div>
</template>
