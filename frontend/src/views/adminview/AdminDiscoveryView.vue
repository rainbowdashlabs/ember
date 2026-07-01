/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import DiscoveryPanels from './admindiscoveryview/DiscoveryPanels.vue'

const {t} = useI18n()
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div>
        <p class="text-sm text-(--text-muted)">{{ t('adminDiscovery.subtitle') }}</p>
      </div>
      <DiscoveryPanels/>
    </div>
  </ViewContent>
</template>
