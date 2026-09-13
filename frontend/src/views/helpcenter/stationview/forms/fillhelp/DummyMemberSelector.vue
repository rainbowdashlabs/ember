/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import InfoContainer from '@/components/container/InfoContainer.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import type {MemberOption} from '@/components/input/select/memberOption'
import FieldHint from '@/components/typography/FieldHint.vue'

const {t} = useI18n()

const dummyMembers: MemberOption[] = [{value: '1', name: 'Max Mustermann'}]
</script>

<template>
  <InfoContainer class="mb-4">
    <div class="space-y-2">
      <FieldHint>{{ t('forms.fillForWhom') }}</FieldHint>
      <MemberSelectInput
          model-value=""
          disabled
          clearable
          :members="dummyMembers"
          :empty-label="t('forms.fillForSelfDefault')"
      />
    </div>
  </InfoContainer>
</template>
