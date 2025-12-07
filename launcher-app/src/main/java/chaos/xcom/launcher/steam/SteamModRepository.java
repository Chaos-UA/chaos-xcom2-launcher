package chaos.xcom.launcher.steam;

import chaos.db.gen.tables.SteamMod;
import chaos.db.gen.tables.records.SteamModRecord;
import chaos.xcom.launcher.db.property.CrudRepository;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.util.Collection;

import static chaos.db.gen.tables.SteamMod.STEAM_MOD;

@Singleton
public class SteamModRepository extends CrudRepository<SteamModRecord, String> {

    public SteamModRepository(DSLContext dsl) {
        super(dsl, STEAM_MOD);
    }

    public void saveTitle(Collection<SteamModRecord> records) {
        dsl.insertInto(table)
                .columns(table.fields())
                .valuesOfRecords(records)
                .onDuplicateKeyUpdate()
                .set(STEAM_MOD.TITLE, DSL.excluded(STEAM_MOD.TITLE))
                .execute();
    }
}
