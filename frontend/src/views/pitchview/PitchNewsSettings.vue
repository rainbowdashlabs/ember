/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import RestrictionsPanel from '@/views/stationview/news/editview/RestrictionsPanel.vue'
import PublicBlogPanel from '@/views/stationview/news/editview/PublicBlogPanel.vue'
import FederationPanel from '@/views/stationview/news/editview/FederationPanel.vue'
import type {PitchNewsSettings} from './pitchTypes'

/** The panels that decide how far a post reaches, drawn by the application's own editor. */
defineProps<{
  settings: PitchNewsSettings
}>()
</script>

<template>
  <RestrictionsPanel
      :groups="settings.groups" :tags="settings.tags" :members="settings.members"
      :selected-user-types="settings.selectedUserTypes"
      :selected-group-ids="settings.selectedGroupIds"
      :selected-tag-ids="settings.selectedTagIds"
      :selected-member-ids="settings.selectedMemberIds"/>
  <PublicBlogPanel :public-blog="settings.publicBlog"/>
  <FederationPanel
      :partners="settings.partners" :can-federate="settings.canFederate"
      :shared="settings.shared" :scope="settings.scope"
      :partner-ids="settings.partnerIds" :visibility-role="settings.visibilityRole"/>
</template>
