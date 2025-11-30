package chaos.xcom.launcher;

import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@QuarkusMain
public class ChaosXcomLauncherStarter implements QuarkusApplication {

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(FlatIntelliJLaf.class.getName());
        Quarkus.run(ChaosXcomLauncherStarter.class, args);
        log.info("Application started");
    }

    @Override
    public int run(String... args) {
        try {
            AtomicReference<Thread> threadRef = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                threadRef.set(Thread.currentThread());
            });

            threadRef.get().join(); // join to make io.quarkus.arc.Arc not to close here.
            return 0; // exit code
        } catch (Exception e) {
            throw new RuntimeException("Failed to init app", e);
        }
    }
}
