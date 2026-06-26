/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import type {ApplicationTab} from '@/views/adminview/adminapplicationsview/types'

const {t} = useI18n()

const activeTab = defineModel<ApplicationTab>({required: true})
</script>

<template>
  <div class="flex gap-2 border-b border-bg-light-accent dark:border-bg-dark-accent pb-2">
    <SelectionToggleButton :selected="activeTab === 'pending'" size="md"
                           @toggle="activeTab = 'pending'">
      {{ t('adminApplications.pending') }}
    </SelectionToggleButton>
    <SelectionToggleButton :selected="activeTab === 'all'" size="md"
                           @toggle="activeTab = 'all'">
      {{ t('adminApplications.all') }}
    </SelectionToggleButton>
  </div>
</template>
