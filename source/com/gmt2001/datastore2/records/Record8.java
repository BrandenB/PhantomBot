/*
 * Copyright (C) 2016-2023 phantombot.github.io/PhantomBot
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.gmt2001.datastore2.records;

import java.util.function.Supplier;

import org.jooq.Field;
import org.jooq.Row8;
import org.jooq.Table;
import org.jooq.impl.UpdatableRecordImpl;

/**
 * Abstract class which simplifies setup and usage of {@link org.jooq.Record8} on an {@link UpdateableRecordImpl}
 *
 * @author gmt2001
 */
public abstract class Record8 <RR extends Record8<RR, A, B, C, D, E, F, G, H>, A, B, C, D, E, F, G, H>
    extends UpdatableRecordImpl<RR> implements org.jooq.Record8<A, B, C, D, E, F, G, H> {
    private final Supplier<Field<A>> field1Supplier;
    private final Supplier<Field<B>> field2Supplier;
    private final Supplier<Field<C>> field3Supplier;
    private final Supplier<Field<D>> field4Supplier;
    private final Supplier<Field<E>> field5Supplier;
    private final Supplier<Field<F>> field6Supplier;
    private final Supplier<Field<G>> field7Supplier;
    private final Supplier<Field<H>> field8Supplier;

    /**
     * Constructor
     *
     * @param table the {@link Table} which stores this record
     * @param field1Supplier the {@link Supplier} for the {@code A} {@link Field}, which is also the primary key
     * @param field2Supplier the {@link Supplier} for the {@code B} {@link Field}
     * @param field3Supplier the {@link Supplier} for the {@code C} {@link Field}
     * @param field4Supplier the {@link Supplier} for the {@code D} {@link Field}
     * @param field5Supplier the {@link Supplier} for the {@code E} {@link Field}
     * @param field6Supplier the {@link Supplier} for the {@code F} {@link Field}
     * @param field7Supplier the {@link Supplier} for the {@code G} {@link Field}
     * @param field8Supplier the {@link Supplier} for the {@code H} {@link Field}
     */
    protected Record8(Table<RR> table, Supplier<Field<A>> field1Supplier, Supplier<Field<B>> field2Supplier,
        Supplier<Field<C>> field3Supplier, Supplier<Field<D>> field4Supplier, Supplier<Field<E>> field5Supplier,
        Supplier<Field<F>> field6Supplier, Supplier<Field<G>> field7Supplier, Supplier<Field<H>> field8Supplier) {
        super(table);
        this.field1Supplier = field1Supplier;
        this.field2Supplier = field2Supplier;
        this.field3Supplier = field3Supplier;
        this.field4Supplier = field4Supplier;
        this.field5Supplier = field5Supplier;
        this.field6Supplier = field6Supplier;
        this.field7Supplier = field7Supplier;
        this.field8Supplier = field8Supplier;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public org.jooq.Record1<A> key() {
        return (org.jooq.Record1) super.key();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Row8<A, B, C, D, E, F, G, H> fieldsRow() {
        return (Row8) super.fieldsRow();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Row8<A, B, C, D, E, F, G, H> valuesRow() {
        return (Row8) super.valuesRow();
    }

    @Override
    public Field<A> field1() {
        return field1Supplier.get();
    }

    @Override
    public Field<B> field2() {
        return field2Supplier.get();
    }

    @Override
    public Field<C> field3() {
        return field3Supplier.get();
    }

    @Override
    public Field<D> field4() {
        return field4Supplier.get();
    }

    @Override
    public Field<E> field5() {
        return field5Supplier.get();
    }

    @Override
    public Field<F> field6() {
        return field6Supplier.get();
    }

    @Override
    public Field<G> field7() {
        return field7Supplier.get();
    }

    @Override
    public Field<H> field8() {
        return field8Supplier.get();
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public A value1() {
        return (A) this.get(0);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public B value2() {
        return (B) this.get(1);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public C value3() {
        return (C) this.get(2);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public D value4() {
        return (D) this.get(3);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public E value5() {
        return (E) this.get(4);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public F value6() {
        return (F) this.get(5);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public G value7() {
        return (G) this.get(6);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public H value8() {
        return (H) this.get(7);
    }

    @Override
    public org.jooq.Record8<A, B, C, D, E, F, G, H> value1(A value) {
        this.set(0, value);
        return this;
    }

    @Override
    public org.jooq.Record8<A, B, C, D, E, F, G, H> value2(B value) {
        this.set(1, value);
        return this;
    }

    @Override
    public org.jooq.Record8<A, B, C, D, E, F, G, H> value3(C value) {
        this.set(2, value);
        return this;
    }

    @Override
    public org.jooq.Record8<A, B, C, D, E, F, G, H> value4(D value) {
        this.set(3, value);
        return this;
    }

    @Override
    public org.jooq.Record8<A, B, C, D, E, F, G, H> value5(E value) {
        this.set(4, value);
        return this;
    }

    @Override
    public org.jooq.Record8<A, B, C, D, E, F, G, H> value6(F value) {
        this.set(5, value);
        return this;
    }

    @Override
    public org.jooq.Record8<A, B, C, D, E, F, G, H> value7(G value) {
        this.set(6, value);
        return this;
    }

    @Override
    public org.jooq.Record8<A, B, C, D, E, F, G, H> value8(H value) {
        this.set(7, value);
        return this;
    }

    @Override
    public org.jooq.Record8<A, B, C, D, E, F, G, H> values(A t1, B t2, C t3, D t4, E t5, F t6, G t7, H t8) {
        return this.value1(t1).value2(t2).value3(t3).value4(t4).value5(t5).value6(t6)
            .value7(t7).value8(t8);
    }

    @Override
    public A component1() {
        return this.value1();
    }

    @Override
    public B component2() {
        return this.value2();
    }

    @Override
    public C component3() {
        return this.value3();
    }

    @Override
    public D component4() {
        return this.value4();
    }

    @Override
    public E component5() {
        return this.value5();
    }

    @Override
    public F component6() {
        return this.value6();
    }

    @Override
    public G component7() {
        return this.value7();
    }

    @Override
    public H component8() {
        return this.value8();
    }
}
