package su.hitori.ux;

import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.storage.DataField;
import su.hitori.ux.storage.DataContainer;

public record GenderInfluencedText(String maleVariant, String femaleVariant) {

    public static final DataField<String> GENDER_FIELD = DataField.createString("gender");

    public String determine(DataContainer container) {
        if (!UXConfiguration.I.chat.gender.enabled) return maleVariant();

        return "woman".equalsIgnoreCase(container.get(GENDER_FIELD))
                ? femaleVariant()
                : maleVariant();
    }

}
