/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import type {RouteLocationRaw} from 'vue-router'
import {useI18n} from 'vue-i18n'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {useMemberRowExtras} from './memberRowExtras'
import type {RosterMember} from './useMemberData'

/**
 * The start of a member's row: a tick while choosing whom to export, otherwise the ways into the
 * person's own screens, or a lock where this reader may not touch them. The ways in are links, so a
 * person's page opens in a new tab like any other link.
 */
const props = defineProps<{
  member: RosterMember
  exportMode: boolean
  selected: boolean
  canEdit: boolean
  /** The person's page, or nothing where this reader has none. */
  detailTo: RouteLocationRaw | null
  /** The page the person is edited on, or nothing where this reader has none. */
  editTo: RouteLocationRaw | null
}>()

const emit = defineEmits<{
  toggleSelect: []
}>()

const {t} = useI18n()
const extras = useMemberRowExtras()
const blockedReason = computed(() => extras.blockedReason(props.member.id))
</script>

<template>
  <div class="flex items-center gap-0.5" @click.stop>
    <CheckboxInput v-if="exportMode" :model-value="selected" @update:model-value="emit('toggleSelect')"/>
    <MutedText v-else-if="blockedReason" size="sm" :title="blockedReason">
      <font-awesome-icon :icon="['fas', 'lock']" class="h-3 w-3"/>
    </MutedText>
    <template v-else>
      <IconButton v-if="detailTo" :icon="['fas', 'eye']" :label="t('membersList.detail')" class="text-primary hover:bg-primary/15"
                  :to="detailTo"/>
      <EditButton v-if="canEdit && editTo" :to="editTo"/>
    </template>
  </div>
</template>
