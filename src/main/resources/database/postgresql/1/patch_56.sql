-- A waiting list that writes to nobody.
--
-- An installation reachable only from inside a network sends mail nobody can act on: the links in it
-- point at an address the recipient cannot open, and a registration that waits for a click on such a
-- link never arrives. A list kept for local tracking has the same shape without the network being the
-- reason, because the people on it are spoken to in person.
--
-- Every list that exists keeps writing, which is what it does today. Switching a list off stops the
-- confirmation mail, the invitation, the reminders and the warning together, and with the reminders
-- goes the removal that follows them: nobody may be dropped for not answering a letter that was never
-- sent.

ALTER TABLE ember_schema.waiting_list
    ADD COLUMN sends_mail BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN ember_schema.waiting_list.sends_mail IS
    'True where the list writes to the people on it, which is the state of every list existing before this column. False where it sends nothing at all: a public registration lands without a confirmation link, an invitation is passed on by hand, and the confirmation cycle with its reminders, its warning and its removal does not run.';
