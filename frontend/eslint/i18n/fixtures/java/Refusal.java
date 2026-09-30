public enum Refusal {
    FORM_GONE(Area.FORMS, 1, "gone"),
    FORM_THIRD(Area.FORMS, 3, "third");

    enum Area {
        FORMS("F", "forms");

        Area(String prefix, String name) {
        }
    }

    Refusal(Area area, int number, String text) {
    }
}
