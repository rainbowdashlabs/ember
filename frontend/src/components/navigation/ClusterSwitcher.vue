/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import {useCluster} from '@/composables/useCluster'

const {t} = useI18n()
const {clusterList, loaded, load, currentClusterId, activeCluster, hasClusters, setActiveCluster} = useCluster()

const showModal = ref(false)

onMounted(() => {
  void load()
})

function switchCluster(clusterId: string) {
  setActiveCluster(clusterId)
  // A full reload, because the cluster identity travels on every request and the loaded screens were
  // answered for the cluster we are leaving
  window.location.href = '/cluster'
}
</script>

<template>
  <template v-if="loaded && hasClusters">
    <button
        class="flex items-center gap-2 text-sm text-(--text-muted) hover:text-(--text) transition-colors"
        :disabled="clusterList.length < 2"
        @click="showModal = true"
    >
      <font-awesome-icon :icon="['fas', 'sitemap']" class="h-3.5 w-3.5"/>
      <span>{{ activeCluster?.name ?? t('clusterSwitcher.noCluster') }}</span>
      <font-awesome-icon v-if="clusterList.length > 1" :icon="['fas', 'chevron-right']" class="h-3 w-3"/>
    </button>

    <Modal v-model="showModal">
      <div class="space-y-4">
        <SubHeader>{{ t('clusterSwitcher.title') }}</SubHeader>
        <div class="space-y-2">
          <NeutralContainer
              v-for="cluster in clusterList"
              :key="cluster.uid"
              :class="cluster.uid === currentClusterId ? 'border-primary' : ''"
              class="flex items-center justify-between cursor-pointer hover:border-primary transition-colors"
              @click="switchCluster(cluster.uid)"
          >
            <div class="flex items-center gap-3">
              <font-awesome-icon :icon="['fas', 'sitemap']" class="h-4 w-4 text-(--text-muted)"/>
              <span class="font-medium">{{ cluster.name }}</span>
            </div>
            <font-awesome-icon
                v-if="cluster.uid === currentClusterId"
                :icon="['fas', 'check']"
                class="text-primary"
            />
          </NeutralContainer>
        </div>
      </div>
    </Modal>
  </template>
</template>
