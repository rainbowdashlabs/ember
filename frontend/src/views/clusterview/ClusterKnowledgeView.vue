/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import KnowledgeBaseView from '@/views/stationview/knowledge/KnowledgeBaseView.vue'
import ClusterPublicKbPanel from './clusterknowledgeview/ClusterPublicKbPanel.vue'
import {CLUSTER_KB_ROUTES} from './clusterknowledgeview/clusterKbRoutes'

/**
 * The association's knowledge base, which is the station one over the station the association owns.
 *
 * <p>Nothing here is a second knowledge base. The folders, the articles, the tags, the versions and the
 * restrictions are the ones a station already has; only the addresses the links point at and the question of
 * who may write differ, because both of those are the association's rather than a station's.
 *
 * <p>The one thing it carries of its own is whether the wiki stands on the public web. That is the station's
 * setting, and the association cannot reach the screen that holds it, so it is offered here, above the wiki
 * it governs.
 */
const {t} = useI18n()
</script>

<template>
  <KnowledgeBaseView
      :routes="CLUSTER_KB_ROUTES"
      :subtitle="t('pages.cluster-knowledge.subtitle')"
      :title="t('pages.cluster-knowledge.title')"
  >
    <template #before>
      <ClusterPublicKbPanel/>
    </template>
  </KnowledgeBaseView>
</template>
