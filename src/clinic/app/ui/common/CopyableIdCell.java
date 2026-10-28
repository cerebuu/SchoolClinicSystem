package clinic.app.ui.common;

import clinic.app.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.datatransfer.StringSelection;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

/** Shared renderer and copy interaction for identifier cells in Swing tables. */
public final class CopyableIdCell extends JPanel implements TableCellRenderer {
    private static final int MIN_WIDTH=154, ICON_WIDTH=58;
    private static final String STATE_KEY=CopyableIdCell.class.getName()+".state";
    private static final Set<String> ID_HEADERS=Set.of("ID","Record ID","Patient ID","Transaction ID",
        "Notification ID","Batch ID","User ID","Campus ID","Actor ID","ID Number","Event ID");
    private final JLabel text=new JLabel();
    private final JLabel copy=new JLabel();

    public record Value(String id,String label) {
        public static Value id(String id){return new Value(id,id);}
        public static Value label(String label){return new Value(null,label);}
    }

    private CopyableIdCell() {
        super(new BorderLayout(Theme.SPACE_1,0));
        setOpaque(true);
        setBorder(BorderFactory.createEmptyBorder(0,Theme.SPACE_2,0,Theme.SPACE_1));
        text.setFont(Theme.font(Theme.TYPE_BODY,java.awt.Font.PLAIN));
        copy.setHorizontalAlignment(JLabel.CENTER);
        copy.setFont(Theme.font(Theme.TYPE_CAPTION,java.awt.Font.BOLD));
        copy.setPreferredSize(new Dimension(ICON_WIDTH,1));
        add(text,BorderLayout.CENTER);add(copy,BorderLayout.EAST);
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    public static void install(JTable table) {
        if(table.getClientProperty(STATE_KEY)!=null)return;
        CopyState state=new CopyState(table);
        table.putClientProperty(STATE_KEY,state);
        ToolTipManager.sharedInstance().registerComponent(table);
        configure(table);
        table.getModel().addTableModelListener(event->{
            state.reset();
            configure(table);
        });
        MouseAdapter interactions=new MouseAdapter() {
            private long lastPopup;
            @Override public void mouseClicked(MouseEvent event) {
                if(!SwingUtilities.isLeftMouseButton(event)||event.getClickCount()!=1)return;
                int row=table.rowAtPoint(event.getPoint()),column=table.columnAtPoint(event.getPoint());
                String id=idAt(table,row,column);
                if(id!=null){table.setRowSelectionInterval(row,row);copy(table,row,column,id);}
            }
            @Override public void mousePressed(MouseEvent event){popup(event);}
            @Override public void mouseReleased(MouseEvent event){popup(event);}
            @Override public void mouseExited(MouseEvent event){table.setCursor(Cursor.getDefaultCursor());}
            @Override public void mouseMoved(MouseEvent event) {
                int row=table.rowAtPoint(event.getPoint()),column=table.columnAtPoint(event.getPoint());
                boolean overCopy=row>=0&&idAt(table,row,column)!=null
                    &&event.getX()>=table.getCellRect(row,column,true).x+table.getCellRect(row,column,true).width-ICON_WIDTH;
                table.setCursor(Cursor.getPredefinedCursor(overCopy?Cursor.HAND_CURSOR:Cursor.DEFAULT_CURSOR));
            }
            private void popup(MouseEvent event) {
                if(!event.isPopupTrigger()||System.currentTimeMillis()-lastPopup<250)return;
                lastPopup=System.currentTimeMillis();
                int row=table.rowAtPoint(event.getPoint()),column=table.columnAtPoint(event.getPoint());
                String id=idAt(table,row,column);
                if(id==null)return;
                table.setRowSelectionInterval(row,row);
                JPopupMenu menu=new JPopupMenu();menu.setBackground(Theme.WHITE);
                JMenuItem item=new JMenuItem("Copy ID");item.setFont(Theme.font(Theme.TYPE_BODY,java.awt.Font.PLAIN));
                item.setForeground(Theme.INK);item.setBackground(Theme.WHITE);
                item.addActionListener(action->copy(table,row,column,id));menu.add(item);
                menu.show(table,event.getX(),event.getY());
            }
        };
        table.addMouseListener(interactions);
        table.addMouseMotionListener(interactions);
    }

    public static void copyToClipboard(String id) {
        if(id==null||id.isBlank())return;
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(id),null);
    }

    private static void configure(JTable table) {
        for(int view=0;view<table.getColumnModel().getColumnCount();view++) {
            TableColumn column=table.getColumnModel().getColumn(view);
            int model=column.getModelIndex();
            String header=table.getModel().getColumnName(model);
            boolean idColumn=ID_HEADERS.contains(header);
            for(int row=0;!idColumn&&row<table.getModel().getRowCount();row++)
                idColumn=table.getModel().getValueAt(row,model) instanceof Value;
            if(!idColumn)continue;
            if(!(column.getCellRenderer() instanceof CopyableIdCell))column.setCellRenderer(new CopyableIdCell());
            column.setMinWidth(MIN_WIDTH);
            if(column.getPreferredWidth()<MIN_WIDTH)column.setPreferredWidth(184);
            column.setResizable(true);
        }
    }

    private static String idAt(JTable table,int viewRow,int viewColumn) {
        if(viewRow<0||viewColumn<0)return null;
        TableColumn view=table.getColumnModel().getColumn(viewColumn);
        int row=table.convertRowIndexToModel(viewRow),column=table.convertColumnIndexToModel(viewColumn);
        Object value=table.getModel().getValueAt(row,column);
        if(!(view.getCellRenderer() instanceof CopyableIdCell)&&!(value instanceof Value))return null;
        return value instanceof Value id?id.id():value instanceof String text&&!text.isBlank()?text:null;
    }

    private static void copy(JTable table,int row,int viewColumn,String id) {
        copyToClipboard(id);
        int modelColumn=table.convertColumnIndexToModel(viewColumn),modelRow=table.convertRowIndexToModel(row);
        Object current=table.getModel().getValueAt(modelRow,modelColumn);
        if(current instanceof Value value&&value.id()==null)return;
        CopyState state=(CopyState)table.getClientProperty(STATE_KEY);
        if(state!=null)state.show(modelRow,modelColumn);
    }

    private static String idString(Object value) {
        if(value instanceof Value id)return id.id()==null?id.label():id.id();
        return value==null?"":value.toString();
    }

    private static String shortened(String id) {return id.length()>8?id.substring(0,8)+"…":id;}

    @Override public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,
            boolean focused,int row,int column) {
        String id=idString(value);boolean canCopy=value instanceof Value v?v.id()!=null:value instanceof String&&!id.isBlank();
        CopyState state=(CopyState)table.getClientProperty(STATE_KEY);
        boolean copied=canCopy&&state!=null&&state.isCopied(table.convertRowIndexToModel(row),
            table.convertColumnIndexToModel(column));
        setBackground(selected?table.getSelectionBackground():table.getBackground());
        java.awt.Color foreground=selected?table.getSelectionForeground():Theme.INK;
        setForeground(foreground);text.setForeground(foreground);
        copy.setForeground(selected?table.getSelectionForeground():Theme.MUTED);
        text.setText(canCopy?shortened(id):id);copy.setText(copied?"Copied":"");copy.setIcon(copied?null:new CopyGlyph());
        copy.setToolTipText(null);setToolTipText(canCopy?id:null);
        return this;
    }

    private static final class CopyState {
        private final JTable table;private int row=-1,column=-1;private final Timer timer;
        private CopyState(JTable table){this.table=table;timer=new Timer(1500,event->reset());timer.setRepeats(false);}
        private void show(int row,int column){this.row=row;this.column=column;timer.restart();table.repaint();}
        private boolean isCopied(int row,int column){return this.row==row&&this.column==column;}
        private void reset(){int oldRow=row,oldColumn=column;row=-1;column=-1;timer.stop();
            if(oldRow>=0&&oldColumn>=0&&oldRow<table.getModel().getRowCount()
                    &&oldColumn<table.getModel().getColumnCount()) {
                int viewRow=table.convertRowIndexToView(oldRow),viewColumn=table.convertColumnIndexToView(oldColumn);
                if(viewRow>=0&&viewColumn>=0)table.repaint(table.getCellRect(viewRow,viewColumn,true));}}
    }

    private static final class CopyGlyph implements javax.swing.Icon {
        @Override public int getIconWidth(){return 14;}
        @Override public int getIconHeight(){return 14;}
        @Override public void paintIcon(Component component,Graphics graphics,int x,int y){
            Graphics2D g=(Graphics2D)graphics.create();g.setColor(component.getForeground());
            g.drawRect(x+4,y+2,8,9);g.drawRect(x+1,y+5,8,8);g.dispose();
        }
    }
}
