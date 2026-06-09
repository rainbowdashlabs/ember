/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('procedures.title')" :subtitle="t('procedures.title')">
    <HelpSection :title="t('procedures.title')">
      <p>Abläufe sind Checklisten mit Schritten, die nacheinander abgearbeitet werden können. Du kannst Abläufe manuell erstellen oder aus einer Vorlage erzeugen.</p>
      <p>Jeder Schritt kann abgehakt werden. Manche Schritte können von anderen abhängen, sodass sie erst verfügbar werden, wenn die Voraussetzungen erfüllt sind.</p>
    </HelpSection>

    <HelpSection :title="t('procedures.templates')">
      <p>Vorlagen sind Blaupausen für Abläufe. Manager können Vorlagen mit Schritten und Abhängigkeiten anlegen. Daraus lassen sich dann neue Abläufe erstellen.</p>
    </HelpSection>

    <HelpTip>
      <p>Nutze Vorlagen, um wiederkehrende Abläufe schnell und einheitlich zu erstellen.</p>
    </HelpTip>
  </HelpArticle>
</template>
