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
 * For whom the template is meant, and whether members generate it for themselves, and guardians for each
 * child, and how many days must pass before the same member can generate it again. Self service is
 * refused while the profile lacks data the letter needs, and its documents are never hidden from the member.
 *
 * <p>For whom applies to every document of the template, whoever generates it: the station's managers, a
 * run, a witness, an appointment or the member. An association only offers a template for self service
 * and sets the wait; each of its stations chooses for whom, so the audience is not chosen here.
 */
const draft = defineModel<TemplateDraft>({required: true})

defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
  members: MemberWithName[]
  /** Whether the owner chooses the audience itself, which only a station does. */
  choosesAudience: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-6">
    <NeutralContainer v-if="choosesAudience" class="space-y-4">
      <LabelledField :label="t('documentTemplates.audience')" :help="t('documentTemplates.audienceHelp')">
        <RestrictionPicker v-model="draft.audience" :groups="groups" :tags="tags" :members="members" show-members
                           data-testid="template-audience"/>
      </LabelledField>
    </NeutralContainer>
    <NeutralContainer class="space-y-4">
      <SubHeader>{{ t('documentTemplates.selfServiceTitle') }}</SubHeader>
      <ToggleSetting v-model="draft.selfService"
                     :label="t(choosesAudience ? 'documentTemplates.selfService' : 'documentTemplates.selfServiceOffered')"
                     :hint="t(choosesAudience ? 'documentTemplates.selfServiceHint' : 'documentTemplates.selfServiceOfferedHint')"
                     data-testid="template-self-service"/>
      <template v-if="draft.selfService">
        <LabelledField :label="t('documentTemplates.cooldown')" :help="t('documentTemplates.cooldownHelp')">
          <NumberInput :model-value="draft.cooldownDays" data-testid="template-cooldown"
                       @update:model-value="days => draft.cooldownDays = days ?? 0"/>
        </LabelledField>
      </template>
    </NeutralContainer>
  </div>
</template>
