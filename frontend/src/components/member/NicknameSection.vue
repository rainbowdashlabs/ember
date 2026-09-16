/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SaveButton from '@/components/button/SaveButton.vue'

const props = withDefaults(defineProps<{
  firstName: string
  lastName: string
  /**
   * How to save it on its own, for a screen where this stands by itself. Where a screen saves the
   * whole form at once it passes nothing, and no button of its own is drawn: two buttons both
   * reading "save" on one form is a form where nobody knows which one they pressed.
   */
  action?: () => Promise<void>
  /**
   * What to say about the field, which differs by who is reading it: somebody setting their own name
   * is told what it does for them, and somebody setting another member's is told the same about that
   * member.
   */
  hint?: string
}>(), {hint: '', action: undefined})

const nickname = defineModel<string>({required: true})

const {t} = useI18n()

/** What a list of people will read once this is saved, so the effect is visible before it is. */
const preview = computed(() => {
  const called = nickname.value.trim()
  const first = props.firstName.trim()
  const last = props.lastName.trim()
  if (!called || called.toLowerCase() === first.toLowerCase()) {
    return [first, last].filter(Boolean).join(' ')
  }
  return [first ? `${first} "${called}"` : `"${called}"`, last].filter(Boolean).join(' ')
})
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('profile.nicknameTitle') }}</SectionHeader>
    <div class="space-y-1">
      <FieldLabel>{{ t('profile.nickname') }}</FieldLabel>
      <TextInput v-model="nickname" :maxlength="60" :placeholder="firstName"/>
      <MutedText tag="p" size="sm">{{ props.hint || t('profile.nicknameHint') }}</MutedText>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('profile.nicknamePreview') }}</FieldLabel>
      <MutedText tag="p">{{ preview }}</MutedText>
    </div>
    <SaveButton v-if="props.action" :action="props.action"/>
  </NeutralContainer>
</template>
