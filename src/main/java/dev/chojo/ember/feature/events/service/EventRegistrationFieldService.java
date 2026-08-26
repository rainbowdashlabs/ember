/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.EventTemplateRegistrationField;
import dev.chojo.ember.feature.events.entity.RegistrationFieldValue;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository.FieldEntry;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The questions an event asks of everyone registering for it, and the answers members give.
 *
 * <p>Validation lives here and nowhere else: registering and editing an answer afterwards both go
 * through {@link #validate}, so the two paths cannot drift into different levels of strictness.
 */
@Singleton
public class EventRegistrationFieldService {
    private static final Logger log = LoggerFactory.getLogger(EventRegistrationFieldService.class);

    private final EventRegistrationFieldRepository repository;

    @Inject
    public EventRegistrationFieldService(EventRegistrationFieldRepository repository) {
        this.repository = repository;
    }

    public List<EventRegistrationField> findByEvent(int eventId) {
        return repository.findByEvent(eventId);
    }

    public void replaceFields(int eventId, List<FieldEntry> fields) {
        repository.replaceFields(eventId, fields);
        log.info("Event {} now asks {} question(s)", eventId, fields.size());
    }

    public List<EventTemplateRegistrationField> findByTemplate(int templateId) {
        return repository.findByTemplate(templateId);
    }

    public void replaceTemplateFields(int templateId, List<FieldEntry> fields) {
        repository.replaceTemplateFields(templateId, fields);
        log.info("Event template {} now asks {} question(s)", templateId, fields.size());
    }

    /**
     * Copies a template's questions into a freshly created event. The copies are independent: a
     * later template edit never rewrites questions members have already answered.
     *
     * @param templateId the template being applied
     * @param eventId    the event being created from it
     */
    public void copyTemplateFields(int templateId, int eventId) {
        var fields = repository.findByTemplate(templateId).stream()
                .map(f -> new FieldEntry(f.name(), f.fieldType(), f.config(), f.overview()))
                .toList();
        if (fields.isEmpty()) return;
        repository.replaceFields(eventId, fields);
        log.info("Event {} took {} question(s) from template {}", eventId, fields.size(), templateId);
    }

    public List<RegistrationFieldValue> findValues(int registrationId) {
        return repository.findValues(registrationId);
    }

    /**
     * Groups the answers of many registrations by registration id, so a whole list is rendered
     * from one query.
     *
     * @param registrationIds the registrations being listed
     * @return the answers per registration, empty entries omitted
     */
    public Map<Integer, List<RegistrationFieldValue>> findValuesByRegistration(List<Integer> registrationIds) {
        return repository.findValuesForRegistrations(registrationIds).stream()
                .collect(Collectors.groupingBy(RegistrationFieldValue::registrationId));
    }

    /**
     * Resolves what a registration should carry for an event's questions, before anything is
     * written. Registering validates first and inserts second, so a refused answer never leaves a
     * registration behind.
     *
     * <p>A question with no answer falls back to its configured default. A required question with
     * neither is refused; every other rule - the options of a choice, the range of a number - is
     * checked the same way for a member answering their own registration and for a manager filling
     * one in on their behalf.
     *
     * @param eventId the event being registered for
     * @param answers the answers keyed by question id, as submitted
     * @return the value to store per question id
     * @throws BadRequestResponse when a question is unanswered, unknown, or answered out of range
     */
    public Map<Integer, String> resolveAnswers(int eventId, Map<Integer, String> answers) {
        return resolveAnswers(eventId, answers, true);
    }

    /**
     * Resolves answers for a caller who may not be allowed to see every question.
     *
     * <p>A manager-only question is skipped entirely for such a caller: they were never shown it,
     * so it must not be required of them, and an answer they somehow sent is not theirs to give.
     *
     * @param manages whether the caller holds the event edit right
     */
    public Map<Integer, String> resolveAnswers(int eventId, Map<Integer, String> answers, boolean manages) {
        var fields = repository.findByEvent(eventId).stream()
                .filter(field -> manages || !field.config().managersOnly())
                .toList();
        if (fields.isEmpty()) return Map.of();
        return validate(fields, answers);
    }

    /**
     * The questions a caller may see. A manager-only question is invisible to everyone without the
     * event edit right, which is what stops it leaking through the registration form.
     *
     * @param manages whether the caller holds the event edit right
     */
    public List<EventRegistrationField> findVisibleByEvent(int eventId, boolean manages) {
        return repository.findByEvent(eventId).stream()
                .filter(field -> manages || !field.config().managersOnly())
                .toList();
    }

    /**
     * The ids of the questions a caller may not see, so their answers can be stripped from a
     * response.
     */
    public Set<Integer> hiddenFieldIds(int eventId, boolean manages) {
        if (manages) return Set.of();
        return repository.findByEvent(eventId).stream()
                .filter(field -> field.config().managersOnly())
                .map(EventRegistrationField::id)
                .collect(Collectors.toSet());
    }

    /**
     * Writes resolved answers onto a registration, replacing whatever it carried before.
     *
     * @param registrationId the registration the answers belong to
     * @param resolved       the answers as {@link #resolveAnswers} returned them
     */
    public void persistAnswers(int registrationId, Map<Integer, String> resolved) {
        if (resolved.isEmpty()) return;
        for (var entry : resolved.entrySet()) {
            repository.setValue(registrationId, entry.getKey(), entry.getValue());
        }
    }

    /**
     * Replaces the answers of an existing registration, dropping any that the new set leaves out.
     *
     * <p>Only the questions the caller can see take part. A manager-only answer is not theirs to
     * give and not theirs to erase, so it survives an edit by the member who registered.
     *
     * @param eventId        the event the registration belongs to
     * @param registrationId the registration being updated
     * @param answers        the answers keyed by question id, as submitted
     * @param manages        whether the caller may see the questions reserved for organisers
     * @throws BadRequestResponse when a question is unanswered, unknown, or answered out of range
     */
    public void replaceAnswers(int eventId, int registrationId, Map<Integer, String> answers, boolean manages) {
        var resolved = resolveAnswers(eventId, answers, manages);
        var hidden = hiddenFieldIds(eventId, manages);

        for (var value : repository.findValues(registrationId)) {
            if (hidden.contains(value.fieldId())) continue;
            repository.deleteValue(registrationId, value.fieldId());
        }
        persistAnswers(registrationId, resolved);
        log.debug(
                "Registration {} for event {} now carries {} answer(s), {} question(s) stayed hidden",
                registrationId,
                eventId,
                resolved.size(),
                hidden.size());
    }

    /**
     * Resolves the answers a registration should carry, applying defaults and refusing anything the
     * questions do not allow. Returns the value to store per question id.
     *
     * @param fields  the event's questions
     * @param answers the answers as submitted, keyed by question id
     * @return the value to store per question id
     * @throws BadRequestResponse when a question is unanswered, unknown, or answered out of range
     */
    public Map<Integer, String> validate(List<EventRegistrationField> fields, Map<Integer, String> answers) {
        var known = fields.stream().map(EventRegistrationField::id).collect(Collectors.toSet());
        for (Integer fieldId : answers.keySet()) {
            if (!known.contains(fieldId)) {
                throw new BadRequestResponse("Unknown registration field " + fieldId);
            }
        }

        var resolved = new LinkedHashMap<Integer, String>();
        for (var field : fields) {
            String value = answers.get(field.id());
            if (isBlank(value)) value = field.config().defaultValue();
            if (isBlank(value)) {
                if (field.config().required()) {
                    throw new BadRequestResponse("Field '" + field.name() + "' is required");
                }
                continue;
            }
            validateValue(field, value);
            resolved.put(field.id(), value);
        }
        return resolved;
    }

    private void validateValue(EventRegistrationField field, String value) {
        var config = field.config();
        if (field.fieldType() == EventFieldType.ENUM) {
            var options = config.options();
            if (options != null && !options.isEmpty() && !options.contains(value)) {
                throw new BadRequestResponse("Field '" + field.name() + "' does not allow the value '" + value + "'");
            }
        }
        if (field.fieldType() == EventFieldType.NUMBER) {
            long number;
            try {
                number = Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                throw new BadRequestResponse("Field '" + field.name() + "' expects a number");
            }
            if (config.min() != null && number < config.min()) {
                throw new BadRequestResponse("Field '" + field.name() + "' is below its minimum of " + config.min());
            }
            if (config.max() != null && number > config.max()) {
                throw new BadRequestResponse("Field '" + field.name() + "' is above its maximum of " + config.max());
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
