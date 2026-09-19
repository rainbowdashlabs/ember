/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {StationMember} from '@/api/types'
import {useMemberRowExtras} from './memberRowExtras'

/**
 * The start of a member's row: a tick while choosing whom to export, otherwise the ways into the
 * person's own screens, or a lock where this reader may not touch them.
 */
const props = defineProps<{
  member: StationMember
  exportMode: boolean
  selected: boolean
  canEdit: boolean
}>()

const emit = defineEmits<{
  toggleSelect: []
  navigateDetail: [event: Event]
  navigateEdit: [event: Event]
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
      <IconButton :icon="['fas', 'eye']" :label="t('membersList.detail')" class="text-primary hover:bg-primary/15"
                  @click="emit('navigateDetail', $event)"/>
      <EditButton v-if="canEdit" @click="emit('navigateEdit', $event)"/>
    </template>
  </div>
</template>
