/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import RestrictionsField from '@/components/input/RestrictionsField.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {MemberGroup, UserTag} from '@/api/types'

const {t} = useI18n()

defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
}>()

const selectedUserTypes = defineModel<string[]>('selectedUserTypes', {required: true})
const selectedGroupIds = defineModel<number[]>('selectedGroupIds', {required: true})
const selectedTagIds = defineModel<number[]>('selectedTagIds', {required: true})
</script>

<template>
  <div class="space-y-2">
    <FieldLabel>{{ t('events.restrictToRoles') }}</FieldLabel>
    <p class="text-xs text-(--text-muted)">{{ t('events.restrictToRolesHint') }}</p>
    <RestrictionsField
        :groups="groups"
        :tags="tags"
        v-model:selected-user-types="selectedUserTypes"
        v-model:selected-group-ids="selectedGroupIds"
        v-model:selected-tag-ids="selectedTagIds"
    />
  </div>
</template>
