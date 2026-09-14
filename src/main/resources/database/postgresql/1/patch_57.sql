-- A station can keep the address of its installation off the paper it hands out.
--
-- Every exported PDF prints a line at the foot of the page saying who made it, which page it is, and
-- the address of the instance it came from. That last part is of no use to the reader of a printed
-- sheet and is an address they cannot open anyway when the installation is only reachable from
-- inside its own network, so a station that hands its sheets to outsiders can now leave it off.
--
-- Every station keeps printing it until somebody says otherwise, which is what the product does
-- today. Nothing else about the page changes: the logo, the name of the station and the line saying
-- who made it and when stay where they are.

ALTER TABLE ember_schema.station
    ADD COLUMN pdf_hides_instance_url BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.station.pdf_hides_instance_url IS
    'True where exported PDFs leave the address of this installation off the foot of the page. False, the state of every station before this column, prints it as a link the way it always was. An export can differ from this for one document without changing it.';
