/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import THead from '@/components/table/THead.vue'
import Th from '@/components/table/Th.vue'
import DummyFieldRow from './DummyFieldRow.vue'

const {t} = useI18n()
</script>

<template>
  <table class="w-full text-sm">
    <thead>
      <THead>
        <th class="py-2 px-3 font-medium">{{ t('membersConfig.colName') }}</th>
        <th class="py-2 px-3 font-medium">{{ t('membersConfig.colType') }}</th>
        <Th align="center" class="font-medium">
          <font-awesome-icon :icon="['fas', 'asterisk']" class="h-3 w-3" :title="t('helpCenter.membersConfig.optRequired')"/>
        </Th>
        <Th align="center" class="font-medium">
          <font-awesome-icon :icon="['fas', 'lock']" class="h-3 w-3" :title="t('helpCenter.membersConfig.optReadonly')"/>
        </Th>
        <th class="py-2 px-3 font-medium text-right"></th>
      </THead>
    </thead>
    <tbody>
      <DummyFieldRow name="Telefonnummer" type="Text" :required="true" :readonly="false"/>
      <DummyFieldRow name="Geburtsdatum" type="Geburtsdatum" :required="true" :readonly="true"/>
      <DummyFieldRow name="Kleidergröße" type="Auswahl" :required="false" :readonly="false"/>
    </tbody>
  </table>
</template>
