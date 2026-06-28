package su.hitori.ux.pronouns;

import net.elytrium.serializer.custom.ClassSerializer;
import su.hitori.ux.config.UXConfiguration.PronounsInfluencedText;

import java.util.Map;

public final class PronounsInfluencedTextConverter extends ClassSerializer<PronounsInfluencedText, Map<String, Object>> {

    @Override
    public Map<String, Object> serialize(PronounsInfluencedText from) {
        return Map.of(
                "he_him", from.heHim,
                "she_her", from.sheHer,
                "they_them", from.theyThem
        );
    }

    @Override
    public PronounsInfluencedText deserialize(Map<String, Object> from) {
        String male = (String) from.get("male");
        if(male != null) {
            String female = (String) from.get("female");
            return new PronounsInfluencedText(
                    male,
                    female,
                    female
            );
        }

        return new PronounsInfluencedText(
                (String) from.get("he_him"),
                (String) from.get("she_her"),
                (String) from.get("they_them")
        );
    }


}
