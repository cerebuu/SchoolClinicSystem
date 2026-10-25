package clinic.app.ui.dispense;
import clinic.app.*;
import clinic.app.ui.common.AsyncLoader;
import clinic.model.Medicine;
import clinic.service.PageResult;
import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;

/** A paged dropdown: at most four cached pages, never the whole catalogue. */
public final class MedicinePicker extends JPanel {
    private final JComboBox<Medicine> choices=new JComboBox<>();
    private final JTextField search=new JTextField(12);
    private final JLabel state=new JLabel("Loading medicines...");
    private final JButton prev=Theme.button("Prev",false),next=Theme.button("Next",false);
    private final Map<String,PageResult<Medicine>> cache=new LinkedHashMap<>();
    private final ApplicationContext app;
    private final Runnable changed;
    private Medicine selected;
    private int page,generation;
    private boolean loading;
    private final javax.swing.Timer debounce;
    public MedicinePicker(ApplicationContext app,Runnable changed) {
        super(new BorderLayout(Theme.SPACE_2,Theme.SPACE_2));this.app=app;this.changed=changed;setOpaque(false);
        Theme.field(search);search.getAccessibleContext().setAccessibleName("Search medicines");search.setToolTipText("Search medicine name");
        choices.getAccessibleContext().setAccessibleName("Medicine");Theme.field(choices);
        choices.setRenderer(new DefaultListCellRenderer(){
            public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean active,boolean focus) {
                JLabel label=(JLabel)super.getListCellRendererComponent(list,value,index,active,focus);
                if(value instanceof Medicine m){label.setText(m.getName()+" — stock "+m.getQuantity());label.setEnabled(m.getQuantity()>0);}
                else label.setText("Select medicine");
                label.setBackground(active?Theme.BRAND_TINT:Theme.WHITE);label.setForeground(Theme.INK);return label;
            }
        });
        JPanel bottom=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));bottom.setOpaque(false);
        JButton retry=Theme.button("Refresh",false);bottom.add(prev);bottom.add(next);bottom.add(retry);bottom.add(state);
        add(search,BorderLayout.NORTH);add(choices,BorderLayout.CENTER);add(bottom,BorderLayout.SOUTH);
        choices.addActionListener(e->{
            if(loading)return;
            Medicine value=(Medicine)choices.getSelectedItem();
            if(value!=null&&value.getQuantity()==0){loading=true;choices.getModel().setSelectedItem(selected);loading=false;state.setText("Out of stock — unavailable");return;}
            selected=value;changed.run();
        });
        debounce=new javax.swing.Timer(250,e->{page=0;load();});debounce.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener(){
            private void change(){generation++;debounce.restart();}
            public void insertUpdate(DocumentEvent e){change();}public void removeUpdate(DocumentEvent e){change();}public void changedUpdate(DocumentEvent e){change();}
        });
        prev.addActionListener(e->{page--;load();});next.addActionListener(e->{page++;load();});
        retry.addActionListener(e->refresh());load();
    }
    public Medicine selected(){return selected;}
    public void refresh(){cache.clear();load();}
    private void load() {
        int version=++generation,p=page;String query=search.getText().trim(),key=p+"\n"+query;
        prev.setEnabled(false);next.setEnabled(false);choices.setEnabled(false);state.setText("Loading...");
        PageResult<Medicine> saved=cache.get(key);
        if(saved!=null){showPage(saved);return;}
        AsyncLoader.load(()->app.dispensableMedicines(p,25,query),result->{
            if(version!=generation)return;
            if(cache.size()>=4)cache.remove(cache.keySet().iterator().next());cache.put(key,result);showPage(result);
        },error->{if(version==generation)state.setText("Unable to load. Refresh to retry.");});
    }
    private void showPage(PageResult<Medicine> result) {
        loading=true;
        DefaultComboBoxModel<Medicine> model=new DefaultComboBoxModel<>(result.rows().toArray(Medicine[]::new));
        model.setSelectedItem(selected);choices.setModel(model);loading=false;choices.setEnabled(true);
        prev.setEnabled(page>0);next.setEnabled((long)(page+1)*25<result.total());
        state.setText(result.total()+" medicines | page "+(page+1));
    }
    public void removeNotify(){generation++;debounce.stop();super.removeNotify();}
    public void addNotify(){super.addNotify();load();}
}
