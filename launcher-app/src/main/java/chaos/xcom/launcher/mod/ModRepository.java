package chaos.xcom.launcher.mod;

import chaos.db.gen.tables.Mod;
import chaos.db.gen.tables.records.ModRecord;
import chaos.xcom.launcher.db.property.CrudRepository;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;

@Singleton
public class ModRepository extends CrudRepository<ModRecord, String> {

    public ModRepository(DSLContext dsl) {
        super(dsl, Mod.MOD);
    }

}
