/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import RestrictionsField from '@/components/input/RestrictionsField.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import HintIcon from '@/components/typography/HintIcon.vue'
import type {RestrictionSelection} from '@/api/types'
import type {MemberGroup, UserTag} from '@/api/generated/schema'

/**
 * One audience restriction of an appointment that has been added: what it limits, who it names, and
 * the button that takes it away again, which leaves the appointment open to everybody once more.
 */
const selection = defineModel<RestrictionSelection>({required: true})

defineProps<{
  /** What the restriction limits, such as who may sign up. */
  label: string
  /** How the restriction works, behind a hint icon. */
  hint: string
  groups: MemberGroup[]
  tags: UserTag[]
}>()

const emit = defineEmits<{
  remove: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-2 rounded-theme border border-(--border) p-3" role="group" :aria-label="label">
    <div class="flex items-center justify-between gap-2">
      <span class="flex items-center gap-1 text-sm font-medium">
        {{ label }}
        <HintIcon :text="hint"/>
      </span>
      <SecondaryButton class="!py-1 !px-2 !text-xs" data-testid="restriction-remove" @click="emit('remove')">
        {{ t('common.remove') }}
      </SecondaryButton>
    </div>
    <RestrictionsField v-model="selection" :groups="groups" :tags="tags"/>
  </div>
</template>
