/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {GroupEntry, TagEntry} from '@/api/generated/schema'
import type {RestrictionSelection} from '@/api/types'

/**
 * Whom a run generates for: the members chosen in the member list, said as a count, or an audience of
 * user types, groups and tags whose current members the run takes in. An audience naming nothing takes
 * in every current member.
 */
const audience = defineModel<RestrictionSelection>({required: true})

defineProps<{
  memberIds: number[] | null
  groups: GroupEntry[]
  tags: TagEntry[]
}>()

const {t} = useI18n()
</script>

<template>
  <MutedText v-if="memberIds" size="sm" tag="p" data-testid="bulk-chosen-members">
    {{ t('documentTemplates.bulk.chosenMembers', {count: memberIds.length}) }}
  </MutedText>
  <LabelledField v-else :label="t('documentTemplates.bulk.audience')" :help="t('documentTemplates.bulk.audienceHelp')" hint>
    <RestrictionPicker v-model="audience" :groups="groups" :tags="tags"/>
  </LabelledField>
</template>
