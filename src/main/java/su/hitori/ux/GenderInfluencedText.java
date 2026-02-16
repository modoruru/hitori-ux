package su.hitori.ux;


import su.hitori.ux.storage.DataContainer;
import su.hitori.ux.storage.DataField;

public final class GenderInfluencedText {

    public static final DataField<String> GENDER_FIELD = DataField.createString("gender");

    private final String maleVariant, femaleVariant;

    public GenderInfluencedText(String maleVariant, String femaleVariant) {
        this.maleVariant = maleVariant;
        this.femaleVariant = femaleVariant;
    }

    public String maleVariant() {
        return maleVariant;
    }

    public String femaleVariant() {
        return femaleVariant;
    }

    public String determine(DataContainer container) {
        return "woman".equalsIgnoreCase(container.get(GENDER_FIELD))
                ? femaleVariant()
                : maleVariant();
    }

}
