/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {MemberGroup, MemberWithName, UserTag} from '@/api/generated/schema'
import type {TemplateDraft} from './templateDraft'

/**
 * Whether members generate the document for themselves, and guardians for each child: for whom, and
 * how many days must pass before the same member can generate it again. Self service is refused while
 * the profile lacks data the letter needs, and its documents are never hidden from the member.
 */
const draft = defineModel<TemplateDraft>({required: true})

defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
  members: MemberWithName[]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('documentTemplates.selfServiceTitle') }}</SubHeader>
    <ToggleSetting v-model="draft.selfService" :label="t('documentTemplates.selfService')" :hint="t('documentTemplates.selfServiceHint')"
                   data-testid="template-self-service"/>
    <template v-if="draft.selfService">
      <LabelledField :label="t('documentTemplates.cooldown')" :help="t('documentTemplates.cooldownHelp')">
        <NumberInput :model-value="draft.cooldownDays" data-testid="template-cooldown"
                     @update:model-value="days => draft.cooldownDays = days ?? 0"/>
      </LabelledField>
      <LabelledField :label="t('documentTemplates.audience')" :help="t('documentTemplates.audienceHelp')">
        <RestrictionPicker v-model="draft.audience" :groups="groups" :tags="tags" :members="members" show-members/>
      </LabelledField>
    </template>
  </NeutralContainer>
</template>
