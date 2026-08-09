/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div>
      <p class="font-medium">Was hat dir zuletzt besonders gut gefallen? <span class="text-error">*</span></p>
      <MutedText tag="p" class="mt-0.5">Schreib es einfach mit deinen eigenen Worten.</MutedText>
    </div>
    <TextInput model-value="" :placeholder="t('forms.questionTitle')" disabled />
  </NeutralContainer>
</template>
