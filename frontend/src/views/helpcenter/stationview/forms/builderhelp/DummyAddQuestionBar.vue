/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'

const {t} = useI18n()

const types = ['CHOICE', 'TEXT', 'RATING', 'DATE', 'RANKING', 'LIKERT']
</script>

<template>
  <div class="space-y-1">
    <SecondaryButton :icon="['fas', 'plus']" disabled>{{ t('forms.addQuestion') }}</SecondaryButton>
    <NeutralContainer class="p-0! max-w-56 overflow-hidden">
      <DropdownMenuItem v-for="type in types" :key="type" :icon="['fas', 'plus']" disabled>
        {{ t(`forms.questionTypes.${type}`) }}
      </DropdownMenuItem>
    </NeutralContainer>
  </div>
</template>
