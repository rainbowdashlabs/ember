-- What kind of thing an inventory holds.
--
-- An inventory says who owns its items and whether they come in sizes, and neither answers the
-- question three features have been assuming the answer to: does it hold one thing in many copies,
-- or a drawer of different things. A requirement, an order for three more and a swap of one size for
-- another all only mean something for the first. Offered on a drawer of odds and ends they mean
-- nothing, and a station that used them there got a shelf of nonsense rather than a refusal.
--
-- Every inventory that exists becomes the first kind, because that is the permissive one: nothing a
-- station is doing today stops working on the day this arrives, and marking a drawer as a drawer is
-- something it opts into rather than inherits. Deriving the value from the sizes or from whether a
-- requirement happens to exist was considered and dropped: it would have been right most of the time,
-- and the times it was wrong are an inventory quietly losing something it was using.

ALTER TABLE ember_schema.inventory
    ADD COLUMN homogeneous BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN ember_schema.inventory.homogeneous IS
    'True where the inventory holds one thing in many copies, which is the only kind requirements, procurements and exchanges are offered for. False where it holds a drawer of different things. Every row existing before this column was added is true, deliberately: that is the permissive state.';
