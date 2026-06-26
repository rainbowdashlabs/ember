/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SectionLabel from '@/components/typography/SectionLabel.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <div class="space-y-3">
      <div class="flex items-center justify-between">
        <SectionLabel>1. {{ t('forms.questionTypes.RATING') }}</SectionLabel>
        <div class="flex gap-1">
          <IconButton :icon="['fas', 'chevron-up']" label="Up" class="text-(--text-muted)" disabled />
          <IconButton :icon="['fas', 'chevron-down']" label="Down" class="text-(--text-muted)" disabled />
          <DeleteButton disabled />
        </div>
      </div>
      <TextInput model-value="Wie zufrieden bist du insgesamt?" disabled />
      <TextInput model-value="1 = sehr unzufrieden, 5 = sehr zufrieden" disabled />
      <div class="flex gap-4">
        <FieldLabel inline class="opacity-60">
          <ToggleInput :model-value="true" disabled />
          {{ t('forms.questionRequired') }}
        </FieldLabel>
      </div>
    </div>
  </NeutralContainer>
</template>
