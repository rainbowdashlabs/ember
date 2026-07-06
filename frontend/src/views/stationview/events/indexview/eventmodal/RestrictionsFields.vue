/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import RestrictionsField from '@/components/input/RestrictionsField.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {RestrictionSelection} from '@/components/input/restriction'
import type {MemberGroup, UserTag} from '@/api/types'

const {t} = useI18n()

defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
}>()

const model = defineModel<RestrictionSelection>({required: true})
</script>

<template>
  <div class="space-y-2">
    <FieldLabel>{{ t('events.restrictToRoles') }}</FieldLabel>
    <p class="text-xs text-(--text-muted)">{{ t('events.restrictToRolesHint') }}</p>
    <RestrictionsField
        :groups="groups"
        :tags="tags"
        v-model="model"
    />
  </div>
</template>
