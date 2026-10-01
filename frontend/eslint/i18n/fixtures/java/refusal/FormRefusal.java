public enum FormRefusal implements Refusal {
    FORM_GONE(1, "gone"),
    FORM_THIRD(
            3,
            "third");

    FormRefusal(int number, String text) {
        this.definition = new Definition(Area.FORMS, number, text);
    }
}
