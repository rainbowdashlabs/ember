/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import RestrictionsField from '@/components/input/RestrictionsField.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {MemberGroup, UserTag} from '@/api/types'

const {t} = useI18n()

defineProps<{
    allGroups: MemberGroup[]
    allTags: UserTag[]
}>()

const selectedUserTypes = defineModel<string[]>('selectedUserTypes', { required: true })
const groupIds = defineModel<number[]>('groupIds', { required: true })
const tagIds = defineModel<number[]>('tagIds', { required: true })
</script>

<template>
    <div class="space-y-3 border-t border-bg-light-accent dark:border-bg-dark-accent pt-3">
        <SubHeader class="text-sm">{{ t('kb.restrictions') }}</SubHeader>
        <RestrictionsField
            :groups="allGroups"
            :tags="allTags"
            v-model:selected-user-types="selectedUserTypes"
            v-model:selected-group-ids="groupIds"
            v-model:selected-tag-ids="tagIds"
        />
    </div>
</template>
