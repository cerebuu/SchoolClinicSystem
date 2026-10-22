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
    private static String labels(Container root) {
        StringBuilder result = new StringBuilder();
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel label) result.append(label.getText());
            if (c instanceof Container container) result.append(labels(container));
        }
        return result.toString();
    }
    public static void main(String[] args)throws Exception {
        onEdt(Theme::install);
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
        check(started.await(5, TimeUnit.SECONDS), "first load started");
        onEdt(()->stale.get().setFilter("new"));
        waitFor(()->staleModel.getRowCount()==1&&staleModel.row(0)==2);
        release.countDown();
        onEdt(()->check(staleModel.row(0)==2, "stale query cannot replace latest page"));
        ApplicationContext app=new ApplicationContext(true);
        AtomicReference<JPanel> screen=new AtomicReference<>();
        onEdt(()->screen.set(new clinic.app.ui.auth.LoginPanel(app, ()-> {
        })));
        snapshot(screen.get(), "login");
        app.login.login("AD001", "admin123");
        onEdt(()->screen.set(new AdminShell(app, ()-> {
        })));
        onEdt(()-> {
            Set<String> nav=buttons(screen.get());
            check(nav.contains("Users")&&nav.contains("Campuses")&&!nav.contains("Dispense medicine"), "admin navigation separated");
        });
        waitFor(() -> labels(screen.get()).contains("User accounts:"));
        snapshot(screen.get(), "admin");
        app.login.logout();
        app.login.login("N001", "nurse123");
        onEdt(()->screen.set(new MainShell(app, ()-> {
        })));
        onEdt(()-> {
            Set<String> nav=buttons(screen.get());
            check(nav.contains("Dispense medicine")&&nav.contains("Reports")&&!nav.contains("Users"), "nurse navigation");
        });
        waitFor(() -> labels(screen.get()).contains("Registered patients:"));
        snapshot(screen.get(), "nurse");
        app.login.logout();
        app.login.login("A001", "assist123");
        onEdt(()-> {
            MainShell shell=new MainShell(app, ()-> {
            });
            check(!buttons(shell).contains("Reports"), "assistant reports hidden");
        });
        app.login.logout();
        app.login.login("D001", "doctor123");
        onEdt(()-> {
            MainShell shell=new MainShell(app, ()-> {
            });
            Set<String> nav=buttons(shell);
            check(nav.contains("Reports")&&!nav.contains("Patients")&&!nav.contains("Inventory"), "doctor report-only navigation");
        });
        System.out.println("PASS: "+checks+" paging, background loading, stale-result, and role-navigation checks.");
        System.out.println("Rendered offscreen snapshots: login, admin, nurse, paged-10000. No interactive desktop/dialog test performed.");
    }
}
