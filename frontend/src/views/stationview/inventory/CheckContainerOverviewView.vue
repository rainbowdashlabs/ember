/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'

const {t} = useI18n()
</script>

<template>
  <ViewContent>
    <PageHeader>{{ t('inventory.checkContainer.title') }}</PageHeader>
    <p class="text-sm text-(--text-muted) mb-4">{{ t('inventory.checkContainer.intro') }}</p>
    <NeutralContainer>
      <Alert variant="info">
        {{ t('inventory.checkContainer.placeholder') }}
      </Alert>
    </NeutralContainer>
  </ViewContent>
</template>
