/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import RestrictionsField from '@/components/input/RestrictionsField.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {RestrictionSelection} from '@/components/input/restriction'
import type {MemberGroup, UserTag} from '@/api/types'

const {t} = useI18n()

defineProps<{
    allGroups: MemberGroup[]
    allTags: UserTag[]
}>()

const model = defineModel<RestrictionSelection>({required: true})
</script>

<template>
    <div class="space-y-3 border-t border-bg-light-accent dark:border-bg-dark-accent pt-3">
        <SubHeader class="text-sm">{{ t('kb.restrictions') }}</SubHeader>
        <RestrictionsField
            :groups="allGroups"
            :tags="allTags"
            v-model="model"
        />
    </div>
</template>
