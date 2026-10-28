package clinic.notify;

import clinic.model.NotificationLog;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class NotificationRecipient {
    public record Option(Channel channel, Recipient recipient, String maskedAddress) {}

    private static final Pattern EMAIL=Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE=Pattern.compile("^\\+?\\d{10,13}$");
    private NotificationRecipient() {}

    public static List<Option> options(NotificationLog notification) {
        return optionsForContact(notification.getRecipientContact());
    }

    /** Returns only validated, masked recipients for a saved profile contact. */
    public static List<String> maskedAddresses(String contact) {
        return optionsForContact(contact).stream().map(Option::maskedAddress).distinct().toList();
    }

    private static List<Option> optionsForContact(String contact) {
        if(contact==null||contact.isBlank())return List.of();
        List<Option> options=new ArrayList<>();
        for(String candidate:contact.split("[,;\\n]")) {
            String value=candidate.trim();
            if(EMAIL.matcher(value).matches())
                options.add(new Option(Channel.EMAIL,new Recipient(value),maskEmail(value)));
            else if(PHONE.matcher(value).matches()) {
                String normalized=normalizePhone(value);
                options.add(new Option(Channel.SMS,new Recipient(normalized),maskPhone(normalized)));
            }
        }
        return List.copyOf(options);
    }

    public static Option select(NotificationLog notification,Channel channel) {
        if(notification.getRecipientContact()==null||notification.getRecipientContact().isBlank()) {
            String label=notification.getRecipientLabel();
            throw new IllegalArgumentException((label==null||label.isBlank()?"Recipient":label)+": no contact is on file.");
        }
        return options(notification).stream().filter(option->option.channel()==channel).findFirst()
            .orElseThrow(()->new IllegalArgumentException("The saved contact is not a valid "+channel+" recipient."));
    }

    private static String normalizePhone(String value) {
        String digits=value.startsWith("+")?value.substring(1):value;
        if(digits.startsWith("09")&&digits.length()==11)return "+63"+digits.substring(1);
        return value.startsWith("+")?value:"+"+digits;
    }

    private static String maskEmail(String address) {
        int at=address.indexOf('@');
        return address.substring(0,1)+"***"+address.substring(at);
    }

    private static String maskPhone(String number) {
        String digits=number.substring(1);
        if(digits.startsWith("63")&&digits.length()>=12)
            return "+63 "+digits.charAt(2)+"** *** **"+digits.substring(digits.length()-2);
        return "+"+digits.substring(0,Math.min(2,digits.length()))+" *** **"+digits.substring(Math.max(0,digits.length()-2));
    }
}
