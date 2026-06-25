/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div>
      <p class="font-medium">Was hat dir zuletzt besonders gut gefallen? <span class="text-error">*</span></p>
      <p class="text-xs text-(--text-muted) mt-0.5">Schreib es einfach mit deinen eigenen Worten.</p>
    </div>
    <TextInput model-value="" :placeholder="t('forms.questionTitle')" disabled />
  </NeutralContainer>
</template>
