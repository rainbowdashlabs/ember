/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import PublicBlogPanel from './PublicBlogPanel.vue'
import RestrictionsPanel from './RestrictionsPanel.vue'
import FederationPanel from './FederationPanel.vue'
import type {MemberGroup, StationMember, UserTag} from '@/api/types'
import type {PartnerResponse} from '@/api/federation'

/**
 * Who an entry is for and how far it travels: the public blog, the audience inside the station,
 * and the partner stations it is shared with. Three questions about reach, asked together.
 */
const publicBlog = defineModel<boolean>('publicBlog', {required: true})
const selectedUserTypes = defineModel<string[]>('selectedUserTypes', {required: true})
const selectedGroupIds = defineModel<number[]>('selectedGroupIds', {required: true})
const selectedTagIds = defineModel<number[]>('selectedTagIds', {required: true})
const selectedMemberIds = defineModel<number[]>('selectedMemberIds', {required: true})
const federationShared = defineModel<boolean>('federationShared', {required: true})
const federationScope = defineModel<string>('federationScope', {required: true})
const federationPartnerIds = defineModel<number[]>('federationPartnerIds', {required: true})
const federationVisibilityRole = defineModel<string>('federationVisibilityRole', {required: true})

defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
  members: StationMember[]
  partners: PartnerResponse[]
  canFederate: boolean
}>()
</script>

<template>
  <PublicBlogPanel v-model:public-blog="publicBlog"/>
  <RestrictionsPanel
      v-model:selected-user-types="selectedUserTypes"
      v-model:selected-group-ids="selectedGroupIds"
      v-model:selected-tag-ids="selectedTagIds"
      v-model:selected-member-ids="selectedMemberIds"
      :groups="groups"
      :tags="tags"
      :members="members"
  />
  <FederationPanel
      v-model:shared="federationShared"
      v-model:scope="federationScope"
      v-model:partner-ids="federationPartnerIds"
      v-model:visibility-role="federationVisibilityRole"
      :partners="partners"
      :can-federate="canFederate"
  />
</template>
