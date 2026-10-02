/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    CHOOSE_A_STATION,
    STATION_NOT_HERE,
    NOT_HERE_OR_NOT_YOURS,
    CLUSTER_NOT_HERE,
    NOT_A_STATION_IDENTITY,
    NOT_A_CLUSTER_IDENTITY,
    TOO_MANY_ATTEMPTS,
} from './shared'

const UNEXPECTED_FAULT = 'In Ember ist etwas schiefgegangen und die Anfrage wurde nicht ausgeführt, '
    + 'es wurde nichts gespeichert. Ein erneuter Versuch kann klappen; wenn es weiter passiert, melde es bitte'
const NOT_A_MEMBER_HERE = 'Du bist kein Mitglied dieser Wache'
const SIGN_IN_FIRST = 'Melde dich zuerst an'

/** What the refusals of failures that belong to no one feature say in German, keyed by their `G-` code. */
export default {
    'G-001': UNEXPECTED_FAULT,
    'G-002': UNEXPECTED_FAULT,
    'G-003': 'Etwas mit denselben Angaben gibt es bereits, es wurde nichts gespeichert',
    'G-004': 'Das verweist auf etwas, das es nicht mehr gibt, oder etwas anderes hängt noch daran, '
        + 'es wurde nichts gespeichert',
    'G-005': 'Etwas am Gesendeten passt nicht zu dem, was sich hier speichern lässt, '
        + 'es wurde nichts gespeichert',
    'G-006': 'Jemand hat dasselbe im selben Moment geändert, deshalb wurde diese Änderung verworfen. '
        + 'Ein erneuter Versuch klappt meistens',
    'G-007': 'Das hat zu lange gedauert und wurde abgebrochen, bevor etwas gespeichert wurde. '
        + 'Ein erneuter Versuch kann klappen',
    'G-008': 'Ember konnte seinen Datenspeicher nicht erreichen, es wurde nichts gespeichert. '
        + 'Ein Versuch in einem Moment klappt meistens',
    'G-009': 'Diese Adresse führt zu nichts',
    'G-010': NOT_HERE_OR_NOT_YOURS,
    'G-011': NOT_HERE_OR_NOT_YOURS,
    'G-012': 'Dieses Bild hat zu viele Pixel, um verarbeitet zu werden, es wurde nichts gespeichert. '
        + 'Eine kleinere Fassung davon klappt',
    'G-013': 'Diese Datei ist kein Bild, das sich hier ablegen lässt, es wurde nichts gespeichert. '
        + 'Eine PNG-, JPEG-, GIF- oder WebP-Datei klappt',
    'G-014': 'Dieses Bild ist größer, als es hier sein darf, es wurde nichts gespeichert. '
        + 'Eine kleinere Datei klappt',
    'G-015': CHOOSE_A_STATION,
    'G-016': NOT_A_MEMBER_HERE,
    'G-017': SIGN_IN_FIRST,
    'G-018': TOO_MANY_ATTEMPTS,
    'G-019': SIGN_IN_FIRST,
    'G-020': 'Deine Anmeldung ist abgelaufen. Melde dich erneut an',
    'G-021': STATION_NOT_HERE,
    'G-022': NOT_A_STATION_IDENTITY,
    'G-023': CLUSTER_NOT_HERE,
    'G-024': NOT_A_CLUSTER_IDENTITY,
    'G-025': 'Dafür fehlt dir die nötige Berechtigung',
}
