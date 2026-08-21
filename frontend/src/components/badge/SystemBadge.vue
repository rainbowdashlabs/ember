/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import BaseBadge from './BaseBadge.vue'

const {t} = useI18n()
</script>

<template>
  <!--
    Beside an entry the instance published to every station. It sits where the partner-station badge
    sits on a federated entry and reads the same way: this did not come from anyone in your station.
  -->
  <span class="inline-flex items-center">
    <BaseBadge bg-class="bg-info/70 border border-info/30" class="gap-1 text-[10px]">
      <font-awesome-icon :icon="['fas', 'bullhorn']" class="w-2.5 h-2.5 shrink-0"/>
      <span class="whitespace-nowrap">{{ t('news.systemBadge') }}</span>
    </BaseBadge>
  </span>
</template>
