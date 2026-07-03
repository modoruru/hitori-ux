package su.hitori.ux.pronouns;

// Here are the most popular pronouns.
public enum SupportedPronouns {

    HE_HIM("he/him"),
    SHE_HER("she/her"),
    THEY_THEM("they/them");

    public final String fancy;

    SupportedPronouns(String fancy) {
        this.fancy = fancy;
    }

}
