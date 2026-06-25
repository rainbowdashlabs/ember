/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div>
      <p class="font-medium">Was gefällt dir am besten?</p>
    </div>
    <div class="space-y-1">
      <div class="flex items-center gap-2 px-4 py-3 rounded-lg border-2 border-primary bg-primary/10 text-primary text-sm font-medium">
        <font-awesome-icon :icon="['fas', 'circle-dot']" class="text-primary shrink-0" />
        <span>{{ t('helpCenter.formsFill.dummyChoiceOption1') }}</span>
      </div>
      <div class="flex items-center gap-2 px-4 py-3 rounded-lg border-2 border-bg-light-accent dark:border-bg-dark-accent text-(--text) text-sm font-medium">
        <font-awesome-icon :icon="['fas', 'circle']" class="text-(--text-muted) shrink-0" />
        <span>{{ t('helpCenter.formsFill.dummyChoiceOption2') }}</span>
      </div>
    </div>
  </NeutralContainer>
</template>
