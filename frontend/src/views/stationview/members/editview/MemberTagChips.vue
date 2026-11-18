/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import RuleKeptMark from '@/components/display/RuleKeptMark.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {userTags} from '@/api'
import {TagVisibility, type UserTag} from '@/api/generated/schema'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * The tags of one member as a compact row of chips, each one saved as it is clicked and put back where
 * the save was refused. A tag that takes its members from a rule shows whether the member has it, carries
 * the rule mark and cannot be toggled, because the rule decides.
 */
const props = defineProps<{
  memberId: number
  tags: UserTag[]
  initialTagIds: Set<number>
  /** The tags that take their members from a rule. */
  ruled?: ReadonlySet<number>
}>()

const emit = defineEmits<{
  failed: [failure: Failure]
}>()

const {t} = useI18n()
const tagIds = ref(new Set(props.initialTagIds))

function flip(tagId: number) {
  const next = new Set(tagIds.value)
  if (next.has(tagId)) next.delete(tagId)
  else next.add(tagId)
  tagIds.value = next
}

async function toggle(tagId: number) {
  const wasIn = tagIds.value.has(tagId)
  flip(tagId)
  try {
    const current = await userTags.getTagMembers(tagId)
    const memberIds = wasIn
        ? current.filter(m => m.id !== props.memberId).map(m => m.id)
        : [...current.map(m => m.id), props.memberId]
    await userTags.setTagMembers(tagId, memberIds)
  } catch (e) {
    flip(tagId)
    emit('failed', describeFailure(e, t))
  }
}
</script>

<template>
  <div class="flex flex-wrap gap-2">
    <SelectionToggleButton
        v-for="tag in tags"
        :key="tag.id"
        :selected="tagIds.has(tag.id)"
        :disabled="ruled?.has(tag.id)"
        :title="ruled?.has(tag.id) ? t('memberRules.keptByRuleHint') : undefined"
        @toggle="toggle(tag.id)"
    >
      <RuleKeptMark v-if="ruled?.has(tag.id)"/>
      <font-awesome-icon v-if="tag.visibility === TagVisibility.PRIVATE" :icon="['fas', 'lock']"
                         :title="t('userTags.visibilities.PRIVATE.label')" class="text-xs"/>
      {{ tag.name }}
    </SelectionToggleButton>
    <MutedText v-if="tags.length === 0">{{ t('memberEdit.noTags') }}</MutedText>
  </div>
</template>
