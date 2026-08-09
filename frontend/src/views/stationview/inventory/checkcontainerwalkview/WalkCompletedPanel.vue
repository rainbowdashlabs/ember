/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const emit = defineEmits<{
  back: []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="mb-4">
    <Alert variant="success">{{ t('inventory.checkContainer.completed') }}</Alert>
    <div class="flex gap-2 mt-3">
      <SecondaryButton @click="emit('back')">{{ t('inventory.checkContainer.backToOverview') }}</SecondaryButton>
    </div>
  </NeutralContainer>
</template>
