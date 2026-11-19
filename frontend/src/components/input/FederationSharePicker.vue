/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import HintIcon from '@/components/typography/HintIcon.vue'
import {ShareScope, type PartnerResponse} from '@/api/generated/schema'

const shared = defineModel<boolean>('shared', {required: true})
const scope = defineModel<string>('scope', {required: true})
const partnerIds = defineModel<number[]>('partnerIds', {required: true})

const props = defineProps<{
  partners: PartnerResponse[]
  disabled?: boolean
  noPermissionHint?: string
  /**
   * What sharing means, behind an info icon right beside the switch's label, as the sign-up switch of
   * the appointment form has it: the label and its icon on the left, the switch on the right.
   */
  hint?: string
}>()

const {t} = useI18n()

const labelId = useId()

const activePartners = computed(() =>
    props.partners.filter(p => p.partner.status === 'ACTIVE')
)

function togglePartner(id: number, checked: boolean) {
  if (checked) {
    partnerIds.value = [...partnerIds.value, id]
  } else {
    partnerIds.value = partnerIds.value.filter(pid => pid !== id)
  }
}
</script>

<template>
  <div class="space-y-2">
    <div v-if="hint" class="flex items-center justify-between" data-testid="federation-share-toggle">
      <span class="flex items-center gap-1">
        <span :id="`${labelId}-share`" class="text-sm font-medium">{{ t('federationShare.enable') }}</span>
        <HintIcon :text="hint"/>
      </span>
      <ToggleInput v-model="shared" :disabled="disabled" :aria-labelledby="`${labelId}-share`"/>
    </div>
    <label v-else class="flex items-center gap-3">
      <ToggleInput v-model="shared" :disabled="disabled"/>
      <span class="text-sm">{{ t('federationShare.enable') }}</span>
    </label>

    <p v-if="disabled && noPermissionHint" class="text-xs text-primary italic">{{ noPermissionHint }}</p>

    <template v-if="shared && !disabled">
      <div class="space-y-1">
        <FieldLabel>{{ t('federationShare.scope') }}</FieldLabel>
        <SelectInput :model-value="scope" @update:model-value="scope = String($event ?? ShareScope.ALL_PARTNERS)">
          <option :value="ShareScope.ALL_PARTNERS">{{ t('federationShare.scopeAll') }}</option>
          <option :value="ShareScope.SPECIFIC">{{ t('federationShare.scopeSpecific') }}</option>
        </SelectInput>
      </div>

      <div v-if="scope === ShareScope.SPECIFIC && activePartners.length > 0" class="space-y-1">
        <FieldLabel>{{ t('federationShare.partners') }}</FieldLabel>
        <div class="space-y-1">
          <label v-for="p in activePartners" :key="p.partner.id" class="flex items-center gap-2 text-sm">
            <input
                type="checkbox"
                :checked="partnerIds.includes(p.partner.id)"
                class="accent-primary"
                @change="togglePartner(p.partner.id, ($event.target as HTMLInputElement).checked)"
            />
            {{ p.partnerStationName }}
          </label>
        </div>
      </div>
    </template>

    <template v-else-if="shared && disabled">
      <p class="text-xs text-(--text-muted)">
        {{ scope === ShareScope.ALL_PARTNERS ? t('federationShare.scopeAll') : t('federationShare.scopeSpecific') }}
      </p>
      <div v-if="scope === ShareScope.SPECIFIC" class="text-xs text-(--text-muted)">
        {{ activePartners.filter(p => partnerIds.includes(p.partner.id)).map(p => p.partnerStationName).join(', ') || '-' }}
      </div>
    </template>
  </div>
</template>
