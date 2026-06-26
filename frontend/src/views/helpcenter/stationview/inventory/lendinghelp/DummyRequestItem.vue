/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import InlineDetail from '@/components/typography/InlineDetail.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps<{
  station: string
  range: string
  item: string
}>()
</script>

<template>
  <NeutralContainer>
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
      <div class="flex flex-col gap-0.5">
        <div>
          <span class="font-medium">{{ station }}</span>
          <InlineDetail>{{ range }}</InlineDetail>
        </div>
        <span class="text-xs text-(--text-muted)">{{ item }}</span>
      </div>
      <slot/>
    </div>
  </NeutralContainer>
</template>
