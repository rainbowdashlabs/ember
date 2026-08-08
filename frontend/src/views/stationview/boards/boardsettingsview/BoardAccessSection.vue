/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SubHeader from '@/components/typography/SubHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RestrictionsField from '@/components/input/RestrictionsField.vue'
import { toRestrictionSelection } from '@/components/input/restriction'
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

const restriction = toRestrictionSelection(selectedUserTypes, selectedGroupIds, selectedTagIds)
</script>

<template>
    <NeutralContainer>
        <SubHeader class="text-sm mb-3">{{ title }}</SubHeader>
        <p class="text-xs text-[var(--text-muted)] mb-3">{{ description }}</p>
        <RestrictionsField
            :groups="groups"
            :tags="tags"
            v-model="restriction"
        />
    </NeutralContainer>
</template>
