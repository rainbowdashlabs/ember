/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import {useStationTransferStatus} from '@/composables/useStationTransferStatus'

const {t} = useI18n()
const {status, loaded, load} = useStationTransferStatus()

onMounted(() => {
  if (!loaded.value) load()
})

const url = computed(() => status.value?.targetInstanceUrl ?? '')
</script>

<template>
  <ViewContent :title="t('pages.station-moved.headline')">
    <div class="flex flex-col items-center gap-6 py-8 text-center max-w-2xl mx-auto">
      <font-awesome-icon :icon="['fas', 'map-location-dot']" class="h-16 w-16 text-accent"/>
      <p class="text-text/80">{{ t('pages.station-moved.body') }}</p>
      <a v-if="url" :href="url" rel="noopener" target="_blank" class="contents">
        <PrimaryButton>
          <font-awesome-icon :icon="['fas', 'right-from-bracket']" class="h-4 w-4 mr-2"/>
          {{ t('pages.station-moved.action') }}
        </PrimaryButton>
      </a>
      <p v-if="url" class="text-text/60 break-all">{{ url }}</p>
      <InfoContainer class="mt-4 text-left">
        {{ t('pages.station-moved.hint') }}
      </InfoContainer>
    </div>
  </ViewContent>
</template>
