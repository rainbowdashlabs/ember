public enum GeneralRefusal implements Refusal {
    GENERAL_GONE(1, "gone");

    GeneralRefusal(int number, String text) {
        this.definition = new Definition(Area.GENERAL, number, text);
    }
}
