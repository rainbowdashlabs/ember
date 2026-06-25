/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const {t} = useI18n()

const types = ['CHOICE', 'TEXT', 'RATING', 'DATE', 'RANKING', 'LIKERT']
</script>

<template>
  <div class="flex flex-wrap gap-2">
    <SecondaryButton v-for="type in types" :key="type" :icon="['fas', 'plus']" disabled>
      {{ t(`forms.questionTypes.${type}`) }}
    </SecondaryButton>
  </div>
</template>
