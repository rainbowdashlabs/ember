/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import IconButton from '@/components/button/IconButton.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div>
      <p class="font-medium">{{ t('helpCenter.formsFill.dummyRatingQuestion') }} <span class="text-error">*</span></p>
    </div>
    <div class="flex flex-wrap gap-1">
      <IconButton v-for="n in 5" :key="n" :icon="['fas', 'star']" :label="`Rating ${n}`"
                  :class="n <= 4 ? 'text-primary' : 'text-(--text-muted)'" class="text-2xl sm:text-xl p-1" disabled />
    </div>
  </NeutralContainer>
</template>
