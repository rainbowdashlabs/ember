/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
</script>

<template>
    <HelpArticle title="Typberechtigungen" subtitle="Zusätzliche Berechtigungen pro Mitgliedstyp festlegen">
        <HelpSection title="Was sind Typberechtigungen?">
            <p>Jeder Mitgliedstyp (z.B. Team, Mitglied, Erziehungsberechtigter) hat feste Standardberechtigungen. Hier kannst du für deine Station zusätzliche Berechtigungen festlegen, die alle Mitglieder eines Typs automatisch erhalten.</p>
        </HelpSection>

        <HelpSection title="Wie funktioniert es?">
            <p>Wähle links einen Mitgliedstyp aus. Rechts siehst du dann die Berechtigungen, die du diesem Typ zusätzlich geben kannst. Aktiviere die gewünschten Berechtigungen — sie gelten sofort für alle Mitglieder dieses Typs auf dieser Station.</p>
        </HelpSection>

        <HelpSection title="Beispiel">
            <p>Du möchtest, dass alle Teamer auch die Anwesenheit verwalten können? Wähle den Typ „Team" und aktiviere die Berechtigung „Anwesenheit". So erhält jedes Teammitglied automatisch diese Berechtigung.</p>
        </HelpSection>

        <HelpSection title="Zusammenspiel mit Gruppen">
            <p>Typberechtigungen wirken zusätzlich zu Gruppenberechtigungen und individuellen Berechtigungen. Ein Mitglied erhält die Summe aus:</p>
            <ul class="list-disc ml-4 space-y-1">
                <li>Standardberechtigungen des Mitgliedstyps</li>
                <li>Typberechtigungen der Station (diese Seite)</li>
                <li>Berechtigungen aus Gruppenmitgliedschaften</li>
                <li>Individuell zugewiesene Berechtigungen</li>
            </ul>
        </HelpSection>

        <HelpTip>
            Typberechtigungen sind eine praktische Abkürzung. Statt jedem Teamer einzeln oder über Gruppen Berechtigungen zu geben, legst du sie hier einmal für den gesamten Typ fest.
        </HelpTip>
    </HelpArticle>
</template>
