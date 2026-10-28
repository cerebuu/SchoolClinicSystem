package clinic.model;

import clinic.util.Validation;
import java.util.Locale;

/** Selected medicine identity and its saved ATC metadata; identity uses name only. */
public record MedicineProduct(String name,String atcPrimary,String atcSecondary) {
    public MedicineProduct {
        name=Validation.required(name,"Medicine").trim().replaceAll("\\s+"," ");
        atcPrimary=atcPrimary==null?"":atcPrimary.trim();
        atcSecondary=atcSecondary==null?"":atcSecondary.trim();
    }
    public String key(){return name.toLowerCase(Locale.ROOT);}
    public String displayName(){return name;}
    public String atcLabel(){return atcPrimary+(atcSecondary.isBlank()?"":" / "+atcSecondary);}
}
