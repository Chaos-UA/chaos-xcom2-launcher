package chaos.xcom.launcher.db.property;

import chaos.xcom.launcher.exception.InternalException;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.jooq.*;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Provides common CRUD operations.
 * @param <T> Record type.
 * @param <ID> ID field type.
 */
public abstract class CrudRepository<T extends UpdatableRecord<?>, ID> {

    protected final DSLContext dsl;
    @Getter
    protected final Table<T> table;
    @Getter
    protected final TableField<T, ID> idField;

    @SuppressWarnings("unchecked")
    public CrudRepository(DSLContext dsl, Table<T> table) {
        this.dsl = dsl;
        this.table = table;
        List<TableField<T, ?>> primaryKeyFields = table.getPrimaryKey().getFields();
        if (primaryKeyFields.size() != 1) {
            throw new InternalException("CRUD repository supports single primary key field, actual primary key fields: "
                    + primaryKeyFields.stream().map(Field::getName).toList());
        }
        this.idField = (TableField<T, ID>) primaryKeyFields.get(0);
    }

    public Optional<T> find(ID id) {
        return dsl.selectFrom(table)
                .where(idField.eq(id))
                .fetchOptional();
    }

    public List<T> find(Collection<ID> ids) {
        return dsl.selectFrom(table)
                .where(idField.in(ids))
                .fetch();
    }

    public List<T> findAll() {
        return dsl.selectFrom(table).fetch();
    }

    public boolean delete(ID id) {
        return dsl.deleteFrom(table).where(idField.eq(id)).execute() != 0;
    }

    public int deleteAll() {
        return dsl.deleteFrom(table).execute();
    }

    public int deleteByIds(Collection<ID> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        return dsl.deleteFrom(table).where(idField.in(ids)).execute();
    }

    public int save(T record) {
        return save(List.of(record));
    }

    /**
     * @param records to save, insert if not exist, and update if exist conflict.
     * @return number of affected records.
     */
    public int save(Collection<T> records) {
        if (CollectionUtils.isEmpty(records)) {
            return 0;
        }
        return dsl.insertInto(table)
                .columns(table.fields())
                .valuesOfRecords(records)
                .onDuplicateKeyUpdate()
                .setAllToExcluded()
                .execute();
    }

    public ID insertAndGetId(T record) {
        ID recordId = dsl.insertInto(table)
                .set(record)
                .returning(idField)
                .fetchSingle(idField);
        record.setValue(idField, recordId);
        return recordId;
    }

    public void insert(T record) {
        int result = dsl.insertInto(table)
                .set(record)
                .execute();
        if (result != 1) {
            throw new InternalException().message("Failed to insert record: " + record);
        }
    }

    public void insert(Collection<T> records) {
        if (records.isEmpty()) {
            return;
        }
        dsl.insertInto(table)
                .columns(table.fields())
                .valuesOfRecords(records)
                .execute();
    }

    public void update(T record) {
        record.attach(dsl.configuration());
        record.update();
    }

    public void update(Collection<T> records) {
        if (records.isEmpty()) {
            return;
        }
        dsl.batchUpdate(records).execute();
    }

    public boolean isTableEmpty() {
        return !dsl.fetchExists(table);
    }

}