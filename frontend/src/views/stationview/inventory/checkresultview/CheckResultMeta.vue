/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import type { EnrichedCheckDetail } from '@/api/generated/schema'
import { formatDateTime } from '@/util/format'

/** When a check was made, who made it and, where somebody else reported it, who that was. */
defineProps<{
  detail: EnrichedCheckDetail
}>()

const { t } = useI18n()
</script>

<template>
  <NeutralContainer>
    <div class="text-sm text-(--text-muted)">
      {{ formatDateTime(detail.check.checkedAt) || '-' }}
      &middot; {{ t('inventory.check.checkedBy') }}: {{ detail.checkerFirstName }} {{ detail.checkerLastName }}
      <template v-if="detail.reporterFirstName">
        &middot;
        <span data-testid="check-result-reporter">
          {{ t('inventory.check.reportedBy') }}: {{ detail.reporterFirstName }} {{ detail.reporterLastName }}
        </span>
      </template>
    </div>
  </NeutralContainer>
</template>
