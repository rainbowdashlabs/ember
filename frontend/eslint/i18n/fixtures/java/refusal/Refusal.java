public sealed interface Refusal permits FormRefusal, GeneralRefusal {
    enum Area {
        FORMS("F", "forms"),
        GENERAL("G", "general");

        Area(String prefix, String name) {
        }
    }
}
