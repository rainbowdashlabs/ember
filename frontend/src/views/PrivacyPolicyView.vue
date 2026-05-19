/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {session} from '@/api'
import Spinner from '@/components/feedback/Spinner.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'

const {t} = useI18n()
const html = ref('')
const loading = ref(true)

onMounted(async () => {
  try {
    const data = await session.getPrivacyPolicy()
    html.value = data.html
  } catch { /* ignore */ }
  loading.value = false
})
</script>

<template>
  <div class="flex justify-center px-4 py-12">
    <NeutralContainer class="w-full max-w-3xl">
      <Spinner v-if="loading" size="lg"/>
      <div v-else-if="html" class="legal-content" v-html="html"/>
      <p v-else class="text-(--text-muted)">{{ t('common.error') }}</p>
    </NeutralContainer>
  </div>
</template>
