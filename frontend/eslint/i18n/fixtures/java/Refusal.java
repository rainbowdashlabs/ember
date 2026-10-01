public enum Refusal {
    FORM_GONE(Area.FORMS, 1, "gone"),
    FORM_THIRD(Area.FORMS, 3, "third"),
    GENERAL_GONE(Area.GENERAL, 1, "gone");

    enum Area {
        FORMS("F", "forms"),
        GENERAL("G", "general");

        Area(String prefix, String name) {
        }
    }

    Refusal(Area area, int number, String text) {
    }
}
