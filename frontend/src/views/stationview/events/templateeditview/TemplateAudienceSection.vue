/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import AudienceRestrictions from '../eventshared/AudienceRestrictions.vue'
import type {RestrictionSelection} from '@/api/types'
import type {MemberGroup, UserTag} from '@/api/generated/schema'

/**
 * What the appointments written from this template start with: who they are for, and who may know
 * they exist, picked the way the appointment form picks them. Each is added with its own button and
 * shows on its own once it names somebody.
 *
 * <p>Neither is a lock on the template itself, because nobody attends a template. Both are handed to
 * the appointment when the template is applied, and the appointment can be widened or narrowed
 * afterwards without the template noticing. The folded section around it carries the heading and the
 * card.
 */
defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
}>()

const restriction = defineModel<RestrictionSelection>('restriction', {required: true})
const viewRestriction = defineModel<RestrictionSelection>('viewRestriction', {required: true})

const {t} = useI18n()
</script>

<template>
  <AudienceRestrictions
      v-model:registration="restriction"
      v-model:visibility="viewRestriction"
      :groups="groups"
      :tags="tags"
      :registration-hint="t('eventTemplates.restrictionHint')"
      :visibility-hint="t('eventTemplates.viewRestrictionHint')"
  />
</template>
