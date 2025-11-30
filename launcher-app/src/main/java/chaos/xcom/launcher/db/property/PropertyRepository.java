package chaos.xcom.launcher.db.property;

import chaos.db.gen.tables.records.PropertyRecord;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;

import java.util.Collection;
import java.util.List;

import static chaos.db.gen.tables.Property.PROPERTY;


@Singleton
public class PropertyRepository extends CrudRepository<PropertyRecord, String> {

    public PropertyRepository(DSLContext dslContext) {
        super(dslContext, PROPERTY);
    }

    public List<PropertyRecord> getAll() {
        return dsl.selectFrom(PROPERTY).fetch();
    }
}
