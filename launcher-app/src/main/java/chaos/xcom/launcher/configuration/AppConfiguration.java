package chaos.xcom.launcher.configuration;

import jakarta.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import javax.sql.DataSource;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class AppConfiguration {

    @Singleton
    public ScheduledThreadPoolExecutor scheduledThreadPoolExecutor() {
        return new ScheduledThreadPoolExecutor(0, Thread.ofVirtual().factory());
    }

    @Singleton // AgroalDataSource
    public DSLContext dslContext(DataSource dataSource) throws Exception {
        return DSL.using(dataSource, SQLDialect.SQLITE);
    }
}
