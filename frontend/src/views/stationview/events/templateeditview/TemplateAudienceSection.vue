/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import RestrictionsField from '@/components/input/RestrictionsField.vue'
import type {RestrictionSelection} from '@/components/input/restriction'
import type {MemberGroup, UserTag} from '@/api/types'

/**
 * What the appointments written from this template start with: who they are for, and who may know
 * they exist.
 *
 * <p>Neither is a lock on the template itself, because nobody attends a template. Both are handed to
 * the appointment when the template is applied, and the appointment can be widened or narrowed
 * afterwards without the template noticing.
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
  <NeutralContainer class="space-y-2">
    <FieldLabel>{{ t('eventTemplates.restriction') }}</FieldLabel>
    <p class="text-xs text-(--text-muted)">{{ t('eventTemplates.restrictionHint') }}</p>
    <RestrictionsField v-model="restriction" :groups="groups" :tags="tags"/>

    <FieldLabel>{{ t('eventTemplates.viewRestriction') }}</FieldLabel>
    <p class="text-xs text-(--text-muted)">{{ t('eventTemplates.viewRestrictionHint') }}</p>
    <RestrictionsField v-model="viewRestriction" :groups="groups" :tags="tags"/>
  </NeutralContainer>
</template>
