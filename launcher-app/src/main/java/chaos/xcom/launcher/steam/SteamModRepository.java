package chaos.xcom.launcher.steam;

import chaos.db.gen.tables.records.SteamModRecord;
import chaos.xcom.launcher.db.property.CrudRepository;
import chaos.xcom.launcher.util.Longs;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;

import java.time.Instant;

import static chaos.db.gen.tables.SteamMod.STEAM_MOD;

@Singleton
public class SteamModRepository extends CrudRepository<SteamModRecord, String> {

    public SteamModRepository(DSLContext dsl) {
        super(dsl, STEAM_MOD);
    }

    public int updateDownloadedAt(long steamModId, Instant downloadedAt) {
        return dsl.update(STEAM_MOD)
                .set(STEAM_MOD.LAST_DOWNLOAD_AT, downloadedAt)
                .where(STEAM_MOD.ID.eq(Longs.toString(steamModId)))
                .execute();
    }

    public int eraseLastDownloadedAtForAll() {
        return dsl.update(STEAM_MOD)
                .setNull(STEAM_MOD.LAST_DOWNLOAD_AT)
                .execute();
    }
}
