/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SubHeader from '@/components/typography/SubHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RestrictionsField from '@/components/input/RestrictionsField.vue'
import type { PermissionGrant, MemberGroup, UserTag } from '@/api/types'

defineProps<{
    title: string
    description: string
    roles: PermissionGrant[]
    groups: MemberGroup[]
    tags: UserTag[]
}>()

const selectedUserTypes = defineModel<string[]>('selectedUserTypes', { required: true })
const selectedGroupIds = defineModel<number[]>('selectedGroupIds', { required: true })
const selectedTagIds = defineModel<number[]>('selectedTagIds', { required: true })
</script>

<template>
    <NeutralContainer>
        <SubHeader class="text-sm mb-3">{{ title }}</SubHeader>
        <p class="text-xs text-[var(--text-muted)] mb-3">{{ description }}</p>
        <RestrictionsField
            :roles="roles"
            :groups="groups"
            :tags="tags"
            v-model:selected-user-types="selectedUserTypes"
            v-model:selected-group-ids="selectedGroupIds"
            v-model:selected-tag-ids="selectedTagIds"
        />
    </NeutralContainer>
</template>
