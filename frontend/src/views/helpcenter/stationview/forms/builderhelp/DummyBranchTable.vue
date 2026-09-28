/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const {t} = useI18n()

/** Each option of the deciding question, and the page it leads to. */
const rows = [
  {option: 'helpCenter.formsPages.dummyBranchYes', page: 'helpCenter.formsPages.dummyPageYes'},
  {option: 'helpCenter.formsPages.dummyBranchNo', page: 'helpCenter.formsPages.dummyPageNo'},
]
</script>

<template>
  <NeutralContainer>
    <div class="space-y-3">
      <FieldLabel class="space-y-1">
        {{ t('forms.pages.after') }}
        <SelectInput model-value="next" disabled>
          <option value="next">{{ t('forms.pages.next') }}</option>
        </SelectInput>
      </FieldLabel>
      <FieldLabel class="space-y-1">
        {{ t('forms.branch.question') }}
        <SelectInput model-value="q" disabled>
          <option value="q">{{ t('helpCenter.formsPages.dummyBranchQuestion') }}</option>
        </SelectInput>
      </FieldLabel>
      <div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
        <template v-for="row in rows" :key="row.option">
          <span class="self-center text-sm">{{ t(row.option) }}</span>
          <SelectInput model-value="p" disabled>
            <option value="p">{{ t(row.page) }}</option>
          </SelectInput>
        </template>
      </div>
    </div>
  </NeutralContainer>
</template>
