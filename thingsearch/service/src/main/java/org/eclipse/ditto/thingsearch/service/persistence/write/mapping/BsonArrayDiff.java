/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.thingsearch.service.persistence.write.mapping;

import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_INTERNAL;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_INTERNAL_KEY;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.eclipse.ditto.json.JsonPointer;

import akka.japi.Pair;

/**
 * Diff between 2 BSON arrays. Only used for the flattened key-value array because Ditto has no API for array
 * operations.
 */
final class BsonArrayDiff {

    private static final String ARRAY_ELEM_AT = "$arrayElemAt";
    private static final String CONCAT_ARRAYS = "$concatArrays";
    private static final String SLICE = "$slice";

    static BsonValue diff(final JsonPointer key,
            final BsonArray minuend,
            final BsonArray subtrahend) {

        return diff(key, minuend, subtrahend, (v, j) -> j);
    }

    static BsonDiff diffInternalArray(final BsonArray minuend, final BsonArray subtrahend) {
        final BsonSizeVisitor bsonSizeVisitor = new BsonSizeVisitor();
        final int replacementSize = bsonSizeVisitor.eval(subtrahend);
        if (minuend.equals(subtrahend)) {
            return BsonDiff.empty(replacementSize);
        }
        final JsonPointer internalArrayKey = JsonPointer.of(FIELD_INTERNAL);
        final Map<BsonValue, Integer> kMap = IntStream.range(0, subtrahend.size())
                .boxed()
                .collect(Collectors.toMap(
                        i -> subtrahend.get(i).asDocument().get(FIELD_INTERNAL_KEY),
                        Function.identity(),
                        (x, y) -> x
                ));
        final BiFunction<BsonDocument, Integer, Integer> kMapGet =
                // use 0 as default value to re-use root grant/revoke arrays where possible
                (doc, j) -> kMap.getOrDefault(doc.get(FIELD_INTERNAL_KEY), 0);
        final BsonValue difference = diff(internalArrayKey, minuend, subtrahend, kMapGet);
        return new BsonDiff(
                replacementSize,
                bsonSizeVisitor.eval(difference),
                Stream.of(Pair.create(internalArrayKey, difference)),
                Stream.empty()
        );
    }

    private static BsonValue diff(final JsonPointer key,
            final BsonArray minuend,
            final BsonArray subtrahend,
            final BiFunction<BsonDocument, Integer, Integer> mostSimilarIndex) {
        final List<Element> elements = diffAsElementList(key, minuend, subtrahend, mostSimilarIndex);
        final List<ElementGroup> aggregatedElements = aggregate(elements);
        if (elements.size() - aggregatedElements.size() > 1 && aggregatedElements.size() > 1) {
            // aggregated element groups are suitable for array concatenation syntax.
            final BsonDocument result = new BsonDocument();
            final BsonArray args = new BsonArray();
            for (final var aggregatedElement : aggregatedElements) {
                args.add(aggregatedElement.toAggregatedBsonValue());
            }
            return result.append(CONCAT_ARRAYS, args);
        } else {
            // either no elements are aggregated or all are aggregated.
            // use literal array syntax.
            final BsonArray result = new BsonArray();
            for (final var element : elements) {
                result.add(element.toBsonValue());
            }
            return result;
        }
    }

    private static List<Element> diffAsElementList(final JsonPointer key,
            final BsonArray minuend,
            final BsonArray subtrahend,
            final BiFunction<BsonDocument, Integer, Integer> mostSimilarIndex) {

        final BsonSizeVisitor bsonSizeVisitor = new BsonSizeVisitor();
        final BsonString subtrahendExpr = getPathExpr(key);
        final Map<BsonValue, Integer> subtrahendIndexMap = IntStream.range(0, subtrahend.size()).boxed()
                .collect(Collectors.toMap(subtrahend::get, Function.identity(), (i, j) -> i));
        final List<Element> result = new ArrayList<>(minuend.size());
        for (int j = 0; j < minuend.size(); ++j) {
            final var element = minuend.get(j);
            final Integer i = subtrahendIndexMap.get(element);
            if (i != null) {
                result.add(new Pointer(subtrahendExpr, i));
            } else if (element.isDocument()) {
                final var elementDoc = element.asDocument();
                final int k = mostSimilarIndex.apply(elementDoc, j);
                if (isMostSimilarElementADocument(subtrahend, k)) {
                    final int replaceSize = bsonSizeVisitor.eval(elementDoc);
                    final BsonDiff diff = BsonDiff.minus(element.asDocument(), subtrahend.get(k).asDocument(), false);
                    final BsonDiffList diffList = diff.consumeAndExportToList();
                    final var diffInPipeline = diffList.toBsonInPipeline(getSubtrahendElement(subtrahendExpr, k));
                    final boolean diffSizeIsBetter = diffInPipeline.map(bsonSizeVisitor::eval)
                            .map(diffSize -> diffSize < replaceSize)
                            .orElse(false);
                    if (diffSizeIsBetter) {
                        result.add(new Replace(diffInPipeline.orElseThrow()));
                    } else {
                        result.add(new Replace(element));
                    }
                } else {
                    result.add(new Replace(element));
                }
            } else {
                result.add(new Replace(element));
            }
        }
        return result;
    }

    private static boolean isMostSimilarElementADocument(final BsonArray subtrahend, final int k) {
        return k >= 0 && k < subtrahend.size() && subtrahend.get(k).isDocument();
    }

    private static BsonDocument getSubtrahendElement(final BsonValue subtrahendExpr, final int i) {
        final BsonArray args = new BsonArray();
        args.add(subtrahendExpr);
        args.add(new BsonInt32(i));
        return new BsonDocument().append(ARRAY_ELEM_AT, args);
    }

    private static BsonString getPathExpr(final JsonPointer key) {
        return new BsonString(StreamSupport.stream(key.spliterator(), false)
                .collect(Collectors.joining(".", "$", "")));
    }

    /**
     * Aggregate array elements to form groups.
     * Pointer elements are aggregated together if they form contiguous sub-arrays.
     * Replacement elements are aggregated together if they appear next to each other.
     *
     * @param elements elements to aggregate into groups.
     * @return aggregated element groups.
     */
    private static List<ElementGroup> aggregate(final List<Element> elements) {
        return elements.stream().reduce(new ArrayList<>(elements.size()),
                BsonArrayDiff::aggregateElement,
                (xs, ys) -> {
                    xs.addAll(ys);
                    return xs;
                });
    }

    private static List<ElementGroup> aggregateElement(final List<ElementGroup> aggregated,
            final Element element) {

        if (aggregated.isEmpty() || !aggregated.get(aggregated.size() - 1).addElementToGroup(element)) {
            // There are no previous groups, or failed to add element to the last aggregated group:
            // Add element as a new group.
            aggregated.add(element.toSingletonGroup());
        }
        return aggregated;
    }

    /**
     * Array element of an array diff. It is either a pointer or a replacement.
     * A pointer points at a location of the previous array denoted by an expression.
     * A replacement is a new value not present in the previous array.
     */
    private interface Element {

        BsonValue toBsonValue();

        ElementGroup toSingletonGroup();
    }

    /**
     * Aggregated group of an array diff. It is either a sub-array or a replacement group.
     * A sub-array is a part of
     */
    private interface ElementGroup {

        BsonValue toAggregatedBsonValue();

        /**
         * Attempt to add an element to this aggregated group.
         *
         * @param element the element to incorporate.
         * @return whether it is successful.
         */
        boolean addElementToGroup(final Element element);
    }

    private static final class Pointer implements Element {

        private final BsonString expr;
        private final int index;

        Pointer(final BsonString expr, final int index) {
            this.expr = expr;
            this.index = index;
        }

        @Override
        public BsonValue toBsonValue() {
            return getSubtrahendElement(expr, index);
        }

        @Override
        public ElementGroup toSingletonGroup() {
            return new SubArray(expr, index, index);
        }
    }

    private static final class Replace implements Element {

        private final BsonValue bson;

        Replace(final BsonValue bson) {
            this.bson = bson;
        }

        @Override
        public BsonValue toBsonValue() {
            return bson;
        }

        @Override
        public ElementGroup toSingletonGroup() {
            return new ReplaceGroup(bson);
        }
    }

    private static final class SubArray implements ElementGroup {

        final BsonString expr;
        final int start;
        int end;

        private SubArray(final BsonString expr, final int start, final int end) {
            this.expr = expr;
            this.start = start;
            this.end = end;
        }

        @Override
        public BsonValue toAggregatedBsonValue() {
            final var args = new BsonArray();
            args.add(expr);
            args.add(new BsonInt32(start));
            args.add(new BsonInt32(end - start + 1));
            return new BsonDocument().append(SLICE, args);
        }

        @Override
        public boolean addElementToGroup(final Element element) {
            if (element instanceof Pointer) {
                final var pointer = (Pointer) element;
                if (end + 1 == pointer.index) {
                    end = pointer.index;
                    return true;
                }
            }
            return false;
        }
    }

    private static final class ReplaceGroup implements ElementGroup {

        private final BsonArray bsonArray;

        private ReplaceGroup(final BsonValue bsonValue) {
            this.bsonArray = new BsonArray();
            bsonArray.add(bsonValue);
        }

        @Override
        public BsonValue toAggregatedBsonValue() {
            return bsonArray;
        }

        @Override
        public boolean addElementToGroup(final Element element) {
            if (element instanceof Replace) {
                bsonArray.add(element.toBsonValue());
                return true;
            }
            return false;
        }
    }

}
