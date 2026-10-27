package clinic.notify;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SimulatedSender implements MessageSender {
    private static final List<String> OUTBOX=new CopyOnWriteArrayList<>();
    private final Channel channel;

    public SimulatedSender(Channel channel) {
        this.channel=java.util.Objects.requireNonNull(channel);
    }

    @Override public SendResult send(Recipient to,String subject,String body) {
        String entry="OUTBOX [SIMULATED] channel="+channel+
            " | recipient="+mask(to.value())+" | SUCCESS";
        OUTBOX.add(entry);
        System.out.println(entry);
        return SendResult.success("Simulated message added to Outbox.",null);
    }

    public static List<String> outbox() { return List.copyOf(OUTBOX); }
    public static void clearOutbox() { OUTBOX.clear(); }

    private static String mask(String value) {
        if(value.contains("@")) {
            int at=value.indexOf('@');
            return value.substring(0,1)+"***"+value.substring(at);
        }
        String digits=value.startsWith("+")?value.substring(1):value;
        return digits.length()<4?"***":"***"+digits.substring(digits.length()-2);
    }
}
