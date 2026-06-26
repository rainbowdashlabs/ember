/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div>
      <p class="font-medium">{{ t('helpCenter.formsFill.dummyDateQuestion') }}</p>
    </div>
    <DateInput model-value="2026-06-15" disabled />
  </NeutralContainer>
</template>
