/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'

/**
 * One audience of the question the help article is showing, so the reader sees the same question put
 * to two kinds of member on different terms.
 */
defineProps<{
  role: string
  writable: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <div
      class="flex flex-wrap items-end gap-3 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 py-2 text-sm">
    <span class="font-medium min-w-32">{{ role }}</span>
    <label class="flex flex-col gap-0.5 text-xs">
      <span class="text-(--text-muted)">{{ t('membersConfig.colWidth') }}</span>
      <SelectInput class="min-w-44" disabled model-value="">
        <option value="">
          {{ t('membersConfig.audiences.inherit', {value: t('membersConfig.widthShortFull')}) }}
        </option>
      </SelectInput>
    </label>
    <label class="flex flex-col gap-0.5 text-xs">
      <span class="text-(--text-muted)">{{ t('membersConfig.writability.column') }}</span>
      <SelectInput class="min-w-44" disabled model-value="">
        <option value="">
          {{ writable
            ? t('membersConfig.writability.station.EVERYONE')
            : t('membersConfig.writability.station.NOT_MEMBER') }}
        </option>
      </SelectInput>
    </label>
    <DeleteButton class="ml-auto" disabled/>
  </div>
</template>
