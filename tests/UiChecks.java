import clinic.app.*;
import clinic.app.nav.*;
import clinic.app.ui.admin.*;
import clinic.app.ui.common.*;
import clinic.service.PageResult;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import javax.swing.*;
public final class UiChecks {
    private static int checks;
    private static void check(boolean ok, String label) {
        if(!ok)throw new AssertionError(label);
        checks++;
    }
    private static void rejects(Class<? extends Throwable> type, Runnable action, String label) {
        try {
            action.run();
        } catch (Throwable error) {
            if (!type.isInstance(error)) throw new AssertionError(label, error);
            checks++;
            return;
        }
        throw new AssertionError(label);
    }
    private static void onEdt(Runnable action)throws Exception {
        SwingUtilities.invokeAndWait(action);
    }
    private static void waitFor(java.util.function.BooleanSupplier ready)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<end) {
            AtomicBoolean done=new AtomicBoolean();
            onEdt(()->done.set(ready.getAsBoolean()));
            if(done.get())return;
            Thread.sleep(15);
        }
        throw new AssertionError("Async operation timed out");
    }
    private static void layout(Component c) {
        if(c instanceof Container ct) {
            ct.doLayout();
            for(Component child:ct.getComponents())layout(child);
        }
    }
    private static void snapshot(JPanel panel, String name)throws Exception {
        onEdt(()-> {
            try {
                panel.setSize(1280, 800);
                layout(panel);
                BufferedImage image=new BufferedImage(1280, 800, BufferedImage.TYPE_INT_RGB);
                Graphics2D g=image.createGraphics();
                panel.printAll(g);
                g.dispose();
                ImageIO.write(image, "png", Path.of("verification", name+".png").toFile());
            }
            catch(Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
    private static Set<String> buttons(Container root) {
        Set<String> result=new HashSet<>();
        for(Component c:root.getComponents()) {
            if(c instanceof JButton b)result.add(b.getText());
            if(c instanceof Container ct)result.addAll(buttons(ct));
        }
        return result;
    }
    private static boolean clickButton(Container root, String text) {
        for(Component c:root.getComponents()) {
            if(c instanceof JButton button && text.equals(button.getText())) {
                button.doClick();
                return true;
            }
            if(c instanceof Container child && clickButton(child, text)) return true;
        }
        return false;
    }
    private static String labels(Container root) {
        StringBuilder result = new StringBuilder();
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel label) result.append(label.getText());
            if (c instanceof Container container) result.append(labels(container));
        }
        return result.toString();
    }
    private static SidebarItem sidebarItem(Container root, String label) {
        for (Component component : root.getComponents()) {
            if (component instanceof SidebarItem item && label.equals(item.getText())) return item;
            if (component instanceof Container child) {
                SidebarItem found = sidebarItem(child, label);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static void checkCurrentPage(Container root, String label) {
        SidebarItem item = sidebarItem(root, label);
        check(item != null, "sidebar item exists: " + label);
        javax.accessibility.AccessibleContext accessible = item.getAccessibleContext();
        check(accessible.getAccessibleStateSet().contains(javax.accessibility.AccessibleState.SELECTED),
            "active navigation is exposed as selected: " + label);
        String description = accessible.getAccessibleDescription();
        check(description != null && description.contains("Current page") && description.contains(label),
            "active navigation description identifies current page: " + label);
    }
    private static void checkButtonStates() {
        JButton primary = Theme.button("Primary", true);
        JButton secondary = Theme.button("Secondary", false);
        check(primary.getBackground().equals(Theme.BRAND) && primary.getForeground().equals(Theme.WHITE),
            "primary button uses brand fill and white text");
        check(secondary.getBackground().equals(Theme.WHITE) && secondary.getForeground().equals(Theme.INK),
            "secondary button uses white fill and ink text");

        primary.getModel().setRollover(true);
        secondary.getModel().setRollover(true);
        check(primary.getBackground().equals(Theme.BRAND_DARK) && primary.getForeground().equals(Theme.WHITE),
            "primary hover keeps white text on dark brand fill");
        check(secondary.getBackground().equals(Theme.BRAND_TINT)
                && secondary.getForeground().equals(Theme.BRAND_DARK),
            "secondary hover uses tinted fill and brand text");

        primary.getModel().setArmed(true);
        primary.getModel().setPressed(true);
        secondary.getModel().setArmed(true);
        secondary.getModel().setPressed(true);
        check(primary.getBackground().equals(new Color(0x7E123B))
                && primary.getForeground().equals(Theme.WHITE),
            "primary pressed state uses deep brand fill and white text");
        check(secondary.getBackground().equals(Theme.BRAND_BORDER)
                && secondary.getForeground().equals(Theme.BRAND_DARK),
            "secondary pressed state uses brand border and brand text");

        primary.setEnabled(false);
        secondary.setEnabled(false);
        check(primary.getBackground().equals(Theme.DISABLED_BG)
                && primary.getForeground().equals(Theme.MUTED),
            "primary disabled state uses muted styling");
        check(secondary.getBackground().equals(Theme.DISABLED_BG)
                && secondary.getForeground().equals(Theme.MUTED),
            "secondary disabled state uses muted styling");
    }
    public static void main(String[] args)throws Exception {
        onEdt(Theme::install);
        onEdt(UiChecks::checkButtonStates);
        PageResult<Integer> last=PagedTableDemo.fetch(399, 25, "");
        check(last.total()==10000&&last.rows().size()==25&&last.rows().get(24)==10000, "last page bounded");
        check(PagedTableDemo.fetch(0, 25, "record 10000").total()==1, "source filtering");
        AtomicReference<PagedTablePanel<Integer>> panel=new AtomicReference<>();
        DummyTableModel model=new DummyTableModel();
        AtomicBoolean background=new AtomicBoolean();
        onEdt(()->panel.set(new PagedTablePanel<>(model, (p, s, q)-> {
            background.set(!SwingUtilities.isEventDispatchThread());
            return PagedTableDemo.fetch(p, s, q);
        })));
        onEdt(()->panel.get().reload());
        waitFor(()->panel.get().totalCount()==10000);
        check(background.get(), "page query runs off EDT");
        onEdt(()-> {
            check(model.getRowCount()==25, "UI retains only 25 rows");
            panel.get().setPage(399);
        });
        waitFor(()->model.getRowCount()==25&&model.row(24)==10000);
        snapshot(panel.get(), "paged-10000");
        onEdt(()->panel.get().setFilter("record 10000"));
        waitFor(()->panel.get().totalCount()==1);
        onEdt(()->check(model.getRowCount()==1, "filter resets page and renders one row"));
        CountDownLatch started=new CountDownLatch(1), release=new CountDownLatch(1);
        AtomicReference<PagedTablePanel<Integer>> stale=new AtomicReference<>();
        DummyTableModel staleModel=new DummyTableModel();
        onEdt(()->stale.set(new PagedTablePanel<>(staleModel, (p, s, q)-> {
            if(q.isEmpty()) {
                started.countDown();
                try {
                    release.await();
                }
                catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return new PageResult<>(List.of(q.isEmpty()?1:2), 1);
        })));
        onEdt(()->stale.get().reload());
        check(started.await(5, TimeUnit.SECONDS), "first load started");
        onEdt(()->stale.get().setFilter("new"));
        waitFor(()->staleModel.getRowCount()==1&&staleModel.row(0)==2);
        release.countDown();
        onEdt(()->check(staleModel.row(0)==2, "stale query cannot replace latest page"));
        AtomicBoolean fail = new AtomicBoolean();
        AtomicReference<PagedTablePanel<Integer>> retry = new AtomicReference<>();
        DummyTableModel retryModel = new DummyTableModel();
        onEdt(() -> retry.set(new PagedTablePanel<>(retryModel, (p, s, q) -> {
            if (fail.get()) throw new IllegalStateException("Test load failure");
            return new PageResult<>(List.of(7), 1);
        })));
        onEdt(() -> retry.get().reload());
        waitFor(() -> retry.get().totalCount() == 1);
        fail.set(true);
        onEdt(() -> retry.get().reload());
        waitFor(() -> labels(retry.get()).contains("Unable to load records"));
        onEdt(() -> check(retry.get().totalCount() == 0 && retryModel.getRowCount() == 0
            && retry.get().selected() == null, "failed load clears count, rows, and selection"));
        fail.set(false);
        onEdt(() -> retry.get().reload());
        waitFor(() -> retry.get().totalCount() == 1);
        onEdt(() -> check(retryModel.row(0) == 7, "failed table load can be retried"));
        checkRefreshKeepsPageAndSelection();
        ApplicationContext app=new ApplicationContext(true);
        AtomicReference<JPanel> screen=new AtomicReference<>();
        onEdt(()->screen.set(new clinic.app.ui.auth.LoginPanel(app, ()-> {
        })));
        snapshot(screen.get(), "login");
        app.login.login("AD001", "admin123");
        onEdt(()->screen.set(new SuperadminShell(app, ()-> {
        })));
        onEdt(()-> {
            Set<String> nav=buttons(screen.get());
            check(nav.contains("Users")&&nav.contains("Campuses")&&!nav.contains("Medication history"), "admin navigation separated");
            checkCurrentPage(screen.get(), "Dashboard");
        });
        snapshot(screen.get(), "admin");
        onEdt(()-> {
            check(screen.get() instanceof SuperadminShell, "administrator receives Superadmin workspace");
            check(Router.createWorkspace(app, ()-> {}) instanceof SuperadminShell,
                "login router selects Superadmin workspace");
            rejects(SecurityException.class, () ->
                Router.createWorkspace(app, ()-> {}, clinic.model.SchoolNurse.class),
                "nurse launcher rejects administrator account");
            rejects(SecurityException.class, () -> new NurseShell(app, ()-> {
            }), "administrator cannot open nurse workspace");
        });
        onEdt(()-> {
            check(clickButton(screen.get(), "Users"), "users page navigation");
            checkCurrentPage(screen.get(), "Users");
        });
        snapshot(screen.get(), "users");
        for (String destination : List.of("Campuses", "Audit log")) {
            onEdt(() -> check(clickButton(screen.get(), destination), "admin navigation: " + destination));
            snapshot(screen.get(), "admin-" + destination.toLowerCase().replace(' ', '-'));
        }
        app.login.logout();
        app.login.login("N001", "nurse123");
        onEdt(()->screen.set(new NurseShell(app, ()-> {
        })));
        onEdt(()-> {
            Set<String> nav=buttons(screen.get());
            check(nav.contains("Medication history")&&nav.contains("Reports")&&nav.contains("AI Nurse Assistant")&&!nav.contains("Users"), "nurse navigation");
            check(nav.contains("Nurse dashboard"), "nurse has dedicated dashboard navigation");
            checkCurrentPage(screen.get(), "Nurse dashboard");
            check(screen.get() instanceof NurseShell, "nurse receives dedicated workspace");
            check(Router.createWorkspace(app, ()-> {}) instanceof NurseShell,
                "login router selects nurse workspace");
            rejects(SecurityException.class, () ->
                Router.createWorkspace(app, ()-> {}, clinic.model.Administrator.class),
                "Superadmin launcher rejects nurse account");
            rejects(SecurityException.class, () -> new SuperadminShell(app, ()-> {
            }), "nurse cannot open Superadmin workspace");
            rejects(SecurityException.class, () -> new MainShell(app, ()-> {
            }), "nurse cannot open the generic clinic workspace");
        });
        onEdt(()->check(labels(screen.get()).contains("Registered patients")
            && labels(screen.get()).contains("Today's consultations")
            && labels(screen.get()).contains("Low-stock items")
            && labels(screen.get()).contains("Expiring soon"), "nurse dashboard includes all summary cards"));
        snapshot(screen.get(), "nurse");
        onEdt(()->check(clickButton(screen.get(), "AI Nurse Assistant"), "AI assistant navigation"));
        snapshot(screen.get(), "nurse-ai-assistant");
        onEdt(()->check(clickButton(screen.get(), "Patients"), "patients page navigation"));
        snapshot(screen.get(), "patients");
        onEdt(()->check(clickButton(screen.get(), "Inventory"), "inventory page navigation"));
        snapshot(screen.get(), "inventory");
        for (String destination : List.of("Consultations", "Medication history", "Notifications", "Reports")) {
            onEdt(() -> check(clickButton(screen.get(), destination), "nurse navigation: " + destination));
            snapshot(screen.get(), "nurse-" + destination.toLowerCase().replace(' ', '-'));
        }
        app.clinic().registerPatient(new clinic.model.Student(
            "report-patient", "Report Patient", "test", "BSCS", 1, "A"));
        app.clinic().recordVisit("report-patient", List.of("Headache"), "Test report access", "", "N001");
        app.login.logout();
        app.login.login("A001", "assist123");
        onEdt(()-> {
            MainShell shell=new MainShell(app, ()-> {
            });
            check(!buttons(shell).contains("Reports"), "assistant reports hidden");
            rejects(SecurityException.class, () -> new NurseShell(app, ()-> {
            }), "assistant cannot open nurse workspace");
        });
        app.login.logout();
        app.login.login("D001", "doctor123");
        onEdt(()-> {
            MainShell shell=new MainShell(app, ()-> {
            });
            Set<String> nav=buttons(shell);
            check(nav.contains("Reports")&&!nav.contains("Patients")&&!nav.contains("Inventory"), "doctor report-only navigation");
            check(!nav.contains("Generate selected report"), "doctor cannot generate reports from unrestricted clinic records");
            screen.set(shell);
        });
        check(app.records(0,25,"").total()==0,
            "doctor report query does not expose the clinic-wide consultation list");
        waitFor(()->labels(screen.get()).contains("No reports are assigned to this account."));
        snapshot(screen.get(), "doctor");
        System.out.println("PASS: "+checks+" paging, background loading, stale-result, and role-navigation checks.");
        System.out.println("Rendered offscreen snapshots: login, nurse, doctor, patients, inventory, admin, users, paged-10000. No interactive desktop/dialog test performed.");
    }

    private static void checkRefreshKeepsPageAndSelection() throws Exception {
        AtomicReference<List<Integer>> values=new AtomicReference<>(
            java.util.stream.IntStream.rangeClosed(1,30).boxed().toList());
        AtomicReference<PagedTablePanel<Integer>> panel=new AtomicReference<>();
        DummyTableModel model=new DummyTableModel();
        onEdt(()->panel.set(new PagedTablePanel<>(model,(page,size,query)->{
            List<Integer> current=values.get();
            int start=page*size;
            int end=Math.min(start+size,current.size());
            return new PageResult<>(start>=end?List.of():current.subList(start,end),current.size(),page,size);
        })));
        onEdt(()->panel.get().reload());
        waitFor(()->panel.get().totalCount()==30&&model.getRowCount()==25);
        onEdt(()->panel.get().setPage(1));
        waitFor(()->panel.get().currentPage()==1&&model.getRowCount()==5&&model.row(0)==26);
        onEdt(()->selectRow(panel.get(),1));
        onEdt(()->check(panel.get().selected().equals(27),"current row can be selected before live refresh"));
        values.set(java.util.stream.IntStream.rangeClosed(0,30).boxed().toList());
        onEdt(()->panel.get().reload());
        waitFor(()->model.getRowCount()==6&&model.row(0)==25&&model.row(2)==27);
        onEdt(()->check(panel.get().currentPage()==1&&panel.get().selected().equals(27),
            "live refresh keeps page and selection when the selected row remains visible"));
    }

    private static void selectRow(PagedTablePanel<Integer> panel,int row) {
        try {
            var field=PagedTablePanel.class.getDeclaredField("table");
            field.setAccessible(true);
            ((JTable)field.get(panel)).setRowSelectionInterval(row,row);
        } catch(ReflectiveOperationException error) {
            throw new AssertionError("Could not select the test row.",error);
        }
    }
}
