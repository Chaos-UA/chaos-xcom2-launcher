package chaos.xcom.launcher.mod.rule;

import chaos.db.gen.tables.records.UserModRuleRecord;
import chaos.xcom.launcher.db.property.CrudRepository;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.Record1;

import java.util.List;

import static chaos.db.gen.tables.UserModRule.USER_MOD_RULE;

@Singleton
public class UserModRuleRepository extends CrudRepository<UserModRuleRecord, Long> {

    public UserModRuleRepository(DSLContext dsl) {
        super(dsl, USER_MOD_RULE);
    }

    public List<Long> findNotProvidedUserIds(List<Long> userRuleIds) {
        return dsl.select(USER_MOD_RULE.ID).from(USER_MOD_RULE)
                .where(USER_MOD_RULE.ID.notIn(userRuleIds))
                .fetch().map(Record1::value1);
    }
}
