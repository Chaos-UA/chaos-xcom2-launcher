package chaos.xcom.launcher.db;

import jakarta.inject.Singleton;

/**
 * TODO
 */
@Singleton
public class TransactionSynchronizer {

    public void newTx(Runnable runnable) {
        runnable.run(); // todo
    }

    public void onRollback(Runnable runnable) {
        // todo
    }
}
