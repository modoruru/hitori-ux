package su.hitori.ux.pronouns;

import su.hitori.api.logging.LoggerFactory;
import su.hitori.api.util.SafeUtil;
import su.hitori.ux.config.UXConfiguration;
import su.hitori.ux.storage.DataContainer;
import su.hitori.ux.storage.DataField;
import su.hitori.ux.storage.serialize.JSONCodec;

import java.util.Map;
import java.util.logging.Logger;

public record PronounsInfluencedText(Map<SupportedPronouns, String> variants) {

    public static final SupportedPronouns DEFAULT = SupportedPronouns.HE_HIM;
    public static final Logger LOGGER = LoggerFactory.instance().create();

    public static final DataField<String> GENDER_FIELD = DataField.createString("gender");
    public static final DataField<SupportedPronouns> PRONOUNS_FIELD = new DataField<>(
            "pronouns",
            new JSONCodec<>(
                    SupportedPronouns::name,
                    name -> {
                        SupportedPronouns pronouns = SafeUtil.enumValueOf(SupportedPronouns.class, (String) name);
                        if(pronouns == null) pronouns = DEFAULT;

                        return pronouns;
                    }
            )
    );

    public PronounsInfluencedText(Map<SupportedPronouns, String> variants) {
        if(variants.size() != SupportedPronouns.values().length) throw new IllegalArgumentException("Variants for all pronouns should be presented.");
        this.variants = Map.copyOf(variants);
    }

    public String determine(DataContainer container) {
        if (!UXConfiguration.I.chat.pronouns.enabled) return variants.get(DEFAULT);

        // I fucking hate migrating something
        migrateGenderToPronouns(container);

        return variants.get(container.getOrDefault(PRONOUNS_FIELD, DEFAULT));
    }

    private static void migrateGenderToPronouns(DataContainer container) {
        String genderField = container.get(GENDER_FIELD);
        if(genderField == null) return; // no migration needed

        SupportedPronouns pronouns = DEFAULT;
        if(genderField.equalsIgnoreCase("man")) pronouns = SupportedPronouns.HE_HIM;
        else if(genderField.equalsIgnoreCase("woman")) pronouns = SupportedPronouns.SHE_HER;

        container.set(GENDER_FIELD, null);
        container.set(PRONOUNS_FIELD, pronouns);

        LOGGER.info(String.format(
                "Migrated %s gender %s to %s pronouns",
                container.identifier().gameName(),
                genderField.toUpperCase(),
                pronouns.name()
        ));
    }

}
