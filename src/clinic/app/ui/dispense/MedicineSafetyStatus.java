package clinic.app.ui.dispense;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.Actions;
import clinic.app.ui.common.AsyncLoader;
import clinic.core.ClinicSystem;
import clinic.model.Medicine;
import clinic.model.Patient;
import clinic.model.User;
import clinic.service.SafetyChecker;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JLabel;
import javax.swing.SwingWorker;

/** Session-safe asynchronous display of the clinic's recorded-allergy check. */
final class MedicineSafetyStatus extends JLabel {
    private final ApplicationContext app;
    private final Supplier<Medicine> selection;
    private final Runnable changed;
    private Patient patient;
    private int generation;
    private SwingWorker<?,?> worker;

    MedicineSafetyStatus(ApplicationContext app,Supplier<Medicine> selection,Runnable changed) {
        this.app=app;this.selection=selection;this.changed=changed;
        setName("medicine-safety");setFont(Theme.font(Theme.TYPE_CAPTION,java.awt.Font.PLAIN));
        setBorder(javax.swing.BorderFactory.createEmptyBorder(Theme.SPACE_1,Theme.SPACE_2,0,Theme.SPACE_2));
        setVisible(false);
    }

    void setPatient(Patient value) { patient=value;refresh(); }

    void refresh() {
        int request=++generation;if(worker!=null)worker.cancel(true);
        Medicine medicine=selection.get();
        if(medicine==null){showMessage("",null,false);return;}
        if(patient==null){showMessage("Select a patient to check allergies",Theme.MUTED,false);return;}
        final ClinicSystem clinic;final User owner;final long session;
        try {
            synchronized(app.login){owner=app.current();session=app.login.getSessionVersion();clinic=app.clinic();}
        } catch(RuntimeException failure){showMessage("Unable to check allergies. Try again.",Theme.DANGER,false);return;}
        String patientId=patient.getId(),name=medicine.getName(),key=medicine.getProduct().key();
        showMessage("Checking allergies…",Theme.MUTED,false);
        worker=AsyncLoader.load(()->clinic.checkSafety(patientId,name),warnings->{
            if(!isCurrent(request,patientId,key,session,owner))return;
            List<String> hard=warnings.stream().filter(w->!w.equals(SafetyChecker.ALLERGY_CONFIRMATION)).toList();
            if(!hard.isEmpty())showMessage(String.join(" ",hard),Theme.DANGER,false);
            else if(warnings.contains(SafetyChecker.ALLERGY_CONFIRMATION))
                showMessage("Allergy warning: Review recorded allergies. Staff confirmation is required.",Theme.WARNING,true);
            else showMessage("No allergy conflict found",Theme.SUCCESS,false);
        },failure->{if(isCurrent(request,patientId,key,session,owner))
            showMessage("Unable to check allergies. Try again.",Theme.DANGER,false);});
    }

    private boolean isCurrent(int request,String patientId,String key,long session,User owner) {
        Medicine medicine=selection.get();
        return request==generation&&patient!=null&&patient.getId().equals(patientId)&&medicine!=null
            &&medicine.getProduct().key().equals(key)&&Actions.isCurrentSession(app.login,session,owner);
    }

    private void showMessage(String message,java.awt.Color color,boolean warning) {
        setText(message.isBlank()?" ":message);setToolTipText(message.isBlank()?null:message);
        setVisible(!message.isBlank());setOpaque(warning);setBackground(warning?Theme.WARNING_TINT:Theme.WHITE);
        setForeground(color==null?Theme.MUTED:color);changed.run();
    }

    @Override public void removeNotify() {
        generation++;if(worker!=null)worker.cancel(true);super.removeNotify();
    }
}
