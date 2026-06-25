/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import IconButton from '@/components/button/IconButton.vue'

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <SectionHeader>Ersthelfer</SectionHeader>
    <div class="space-y-1">
      <FieldLabel class="text-(--text-muted)">{{ t('userTags.currentMembers') }}</FieldLabel>
      <div class="space-y-1">
        <div class="flex items-center justify-between rounded-lg px-3 py-2 bg-bg-light-accent dark:bg-bg-dark-accent">
          <span class="text-sm font-medium">Max Mustermann</span>
          <IconButton :icon="['fas', 'xmark']" :label="t('userTags.removeMember')" class="text-error hover:text-error/80" />
        </div>
      </div>
    </div>
    <div class="space-y-1">
      <FieldLabel class="text-(--text-muted)">{{ t('userTags.addMembers') }}</FieldLabel>
      <div class="space-y-1">
        <div class="flex items-center justify-between rounded-lg px-3 py-2 hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent cursor-pointer">
          <span class="text-sm font-medium">Anna Schmidt</span>
          <font-awesome-icon :icon="['fas', 'plus']" class="text-primary text-sm"/>
        </div>
      </div>
    </div>
  </div>
</template>
