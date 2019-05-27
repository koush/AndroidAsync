package com.koushikdutta.async.future;

import android.text.TextUtils;

import com.koushikdutta.async.ByteBufferList;

import org.json.JSONObject;

import java.io.InvalidObjectException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;

public class Converter<R> {
    public static <T> Converter<T> convert(Future<T> future, String mime) {
        return new Converter<>(future, mime);
    }

    public static <T> Converter<T> convert(Future<T> future) {
        return convert(future, null);
    }

    static class MimedData<T> {
        public MimedData(T data, String mime) {
            this.data = data;
            this.mime = mime;
        }
        T data;
        String mime;
    }

    static class MultiTransformer<T, F> extends MultiTransformFuture<MimedData<Future<T>>, MimedData<Future<F>>> {
        TypeConverter<T, F> converter;
        String converterMime;
        int distance;
        public MultiTransformer(TypeConverter<T, F> converter, String converterMime, int distance) {
            this.converter = converter;
            this.converterMime = converterMime;
            this.distance = distance;
        }

        @Override
        protected void transform(MimedData<Future<F>> converting) {
            // transform will only ever be called once, and is called immediately,
            // the transform is on the future itself, and not a pending value.
            // so there's no risk of running the converter twice.
            final String mime = converting.mime;

            // this future will receive the eventual actual value.
            final MultiFuture<T> converted = new MultiFuture<>();

            // this marks the conversion as "complete". the conversion will start
            // as soon as the value is ready.
            setComplete(new MimedData<>(converted, mimeReplace(mime, converterMime)));

            // wait on the incoming value and convert it
            converting.data.thenConvert(data -> converter.convert(data, mime)).
            setCallback((e, result1) -> {
                if (e != null)
                    converted.setComplete(e);
                else
                    converted.setComplete(result1);
            });
        }
    }

    static abstract class EnsureHashMap<K, V> extends LinkedHashMap<K, V> {
        synchronized V ensure(K k) {
            if (!containsKey(k)) {
                put(k, makeDefault());
            }
            return get(k);
        }

        protected abstract V makeDefault();
    }

    static class MimedType<T> {
        MimedType(Class<T> type, String mime) {
            this.type = type;
            this.mime = mime;
        }
        Class<T> type;
        String mime;

        @Override
        public int hashCode() {
            return type.hashCode() ^ mime.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            MimedType other = (MimedType)obj;
            return type.equals(other.type) && mime.equals(other.mime);
        }

        // check if this mimed type is the same or more specific than this mimed type
        public boolean isTypeOf(MimedType other) {
            // check the type, this type must be less specific than the other type
            if (!this.type.isAssignableFrom(other.type))
                return false;

            return isTypeOf(other.mime);
        }

        public String primary() {
            return mime.split("/")[0];
        }

        public String secondary() {
            return mime.split("/")[1];
        }

        // check if this mimed type is convertible to another mimed type
        public boolean isTypeOf(String mime) {
            String[] otherParts = mime.split("/");
            String[] myParts = this.mime.split("/");

            // ensure the other type is the same OR this type is fine with a wildcard
            if (!"*".equals(myParts[0]) && !otherParts[0].equals(myParts[0]))
                return false;

            if (!"*".equals(myParts[1]) && !otherParts[1].equals(myParts[1]))
                return false;

            return true;
        }

        @Override
        public String toString() {
            return type.getSimpleName() + " " + mime;
        }
    }

    static class ConverterTransformers<F, T> extends LinkedHashMap<MimedType<T>, MultiTransformer<T, F>> {
    }

    static class Converters<F, T> extends EnsureHashMap<MimedType<F>, ConverterTransformers<F, T>> {
        @Override
        protected ConverterTransformers makeDefault() {
            return new ConverterTransformers();
        }

        private static <F, T> void add(ConverterTransformers<F, T> set, ConverterTransformers<F, T> more) {
            if (more == null)
                return;
            set.putAll(more);
        }
        public ConverterTransformers<F, T> getAll(MimedType<T> mimedType) {
            ConverterTransformers<F, T> ret = new ConverterTransformers<>();

            for (MimedType candidate: keySet()) {
                if (candidate.isTypeOf(mimedType))
                    add(ret, get(candidate));
            }

            return ret;
        }
    }

    Converters<Object, Object> outputs;

    protected ConverterEntries getConverters() {
        return new ConverterEntries(Converters);
    }

    MultiFuture<R> future = new MultiFuture<>();
    String futureMime;
    protected Converter(Future future, String mime) {
        if (TextUtils.isEmpty(mime))
            mime = MIME_ALL;
        this.futureMime = mime;
        this.future.setComplete(future);
    }

    synchronized private final <T> Future<T> to(Object value, Class<T> clazz, String mime) {
        if (clazz.isInstance(value))
            return new SimpleFuture<>((T) value);
        return to(value.getClass(), clazz, mime);
    }

    synchronized private final <T> Future<T> to(Class fromClass, Class<T> clazz, String mime) {
        if (TextUtils.isEmpty(mime))
            mime = MIME_ALL;

        if (outputs == null) {
            outputs = new Converters<>();
            ConverterEntries converters = getConverters();
            for (ConverterEntry entry: converters.list) {
                outputs.ensure(entry.from).put(entry.to, new MultiTransformer<>(entry.typeConverter, entry.to.mime, entry.distance));
            }
        }

        MimedType<T> target = new MimedType<>(clazz, mime);
        ArrayDeque<PathInfo> bestMatch = new ArrayDeque<>();
        ArrayDeque<PathInfo> currentPath = new ArrayDeque<>();
        if (search(target, bestMatch, currentPath, new MimedType(fromClass, futureMime), new HashSet<MimedType>())) {
            PathInfo current = bestMatch.removeFirst();

            new SimpleFuture<>(new MimedData<>((Future<Object>)future, futureMime)).setCallback(current.transformer);

            while (!bestMatch.isEmpty()) {
                PathInfo next = bestMatch.removeFirst();
                current.transformer.setCallback(next.transformer);
                current = next;
            }

            return ((MultiTransformer<T, Object>)current.transformer).then(from -> from.data);
        }

        return new SimpleFuture<>(new InvalidObjectException("unable to find converter"));
    }

    static class PathInfo {
        MultiTransformer<Object, Object> transformer;
        String mime;
        MimedType candidate;

        static int distance(ArrayDeque<PathInfo> path) {
            int distance = 0;
            for (PathInfo entry: path) {
                distance += entry.transformer.distance;
            }
            return distance;
        }
    }

    static String mimeReplace(String mime1, String mime2) {
        String[] parts = mime2.split("/");
        String[] myParts = mime1.split("/");

        // a wildcard mime converter adopts the mime of the converted type
        String primary = !"*".equals(parts[0]) ? parts[0] : myParts[0];
        String secondary = !"*".equals(parts[1]) ? parts[1] : myParts[1];

        return primary + "/" + secondary;
    }

    public final <T> Future<T> to(Class<T> clazz) {
        return to(clazz, null);
    }

    private <T> boolean search(MimedType<T> target, ArrayDeque<PathInfo> bestMatch, ArrayDeque<PathInfo> currentPath, MimedType currentSearch, HashSet<MimedType> searched) {
        if (target.isTypeOf(currentSearch)) {
            bestMatch.clear();
            bestMatch.addAll(currentPath);
            return true;
        }

        // the current path must have potential to be better than the best match
        if (!bestMatch.isEmpty() && PathInfo.distance(currentPath) >= PathInfo.distance(bestMatch))
            return false;

        // prevent reentrancy
        if (searched.contains(currentSearch))
            return false;

        boolean found = false;
        searched.add(currentSearch);
        ConverterTransformers<Object, Object> converterTransformers = outputs.getAll(currentSearch);
        for (MimedType candidate: converterTransformers.keySet()) {
            // this simulates the mime results of a transform
            MimedType newSearch = new MimedType(candidate.type, mimeReplace(currentSearch.mime, candidate.mime));

            PathInfo path = new PathInfo();
            path.transformer = converterTransformers.get(candidate);
            path.mime = newSearch.mime;
            path.candidate = candidate;
            currentPath.addLast(path);
            try {
                found |= search(target, bestMatch, currentPath, newSearch, searched);
            }
            finally {
                currentPath.removeLast();
            }
        }

        if (found) {
            // if this resulted in a success,
            // clear this from the currentSearch list, because we know this leads
            // to a potential solution. maybe we can arrive here faster.
            searched.remove(currentSearch);
        }

        return found;
    }

    private static final String MIME_ALL = "*/*";
    public <T> Future<T> to(Class<T> clazz, String mime) {
        return future.then(from -> to(from, clazz, mime));
    }

    static class ConverterEntry<F, T> {
        ConverterEntry(Class<F> from, String fromMime, Class<T> to, String toMime, int distance, TypeConverter<T, F> typeConverter) {
            this.from = new MimedType<>(from, fromMime);
            this.to = new MimedType<>(to, toMime);
            this.distance = distance;
            this.typeConverter = typeConverter;
        }
        MimedType<F> from;
        MimedType<T> to;
        int distance;
        TypeConverter<T, F> typeConverter;

        @Override
        public int hashCode() {
            return from.hashCode() ^ to.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            ConverterEntry other = (ConverterEntry)obj;
            return from.equals(other.from) && to.equals(other.to);
        }
    }

    public static class ConverterEntries {
        public ArrayList<ConverterEntry> list = new ArrayList<>();
        public ConverterEntries() {
        }

        public ConverterEntries(ConverterEntries other) {
            list.addAll(other.list);
        }

        public synchronized <F, T> void addConverter(Class<F> from, String fromMime, Class<T> to, String toMime, TypeConverter<T, F> typeConverter) {
            addConverter(from, fromMime, to, toMime, 1, typeConverter);
        }
        public synchronized <F, T> void addConverter(Class<F> from, String fromMime, Class<T> to, String toMime, int distance, TypeConverter<T, F> typeConverter) {
            if (TextUtils.isEmpty(fromMime))
                fromMime = MIME_ALL;
            if (TextUtils.isEmpty(toMime))
                toMime = MIME_ALL;

            list.add(new ConverterEntry<>(from, fromMime, to, toMime, distance, typeConverter));
        }

        public synchronized boolean removeConverter(TypeConverter typeConverter) {
            for (ConverterEntry entry: list) {
                if (entry.typeConverter == typeConverter)
                    return list.remove(entry);
            }
            return false;
        }
    }

    public final static ConverterEntries Converters = new ConverterEntries();

    static {
        // ensure byte buffer operations are idempotent. do deep copies.
        final TypeConverter<ByteBufferList, byte[]> ByteArrayToByteBufferList = (from, fromMime) ->
                new SimpleFuture<>(new ByteBufferList(ByteBufferList.deepCopy(ByteBuffer.wrap(from))));
        final TypeConverter<byte[], ByteBufferList> ByteBufferListToByteArray = (from, fromMime) ->
                new SimpleFuture<>(from.getAllByteArray());
        final TypeConverter<ByteBuffer, ByteBufferList> ByteBufferListToByteBuffer = (from, fromMime) ->
                new SimpleFuture<>(from.getAll());
        final TypeConverter<String, ByteBufferList> ByteBufferListToString = (from, fromMime) ->
                new SimpleFuture<>(from.peekString());
        final TypeConverter<ByteBuffer, byte[]> ByteArrayToByteBuffer = (from, fromMime) ->
                new SimpleFuture<>(ByteBufferList.deepCopy(ByteBuffer.wrap(from)));
        final TypeConverter<ByteBufferList, ByteBuffer> ByteBufferToByteBufferList = (from, fromMime) ->
                new SimpleFuture<>(new ByteBufferList(ByteBufferList.deepCopy(from)));

        final TypeConverter<byte[], String> StringToByteArray = (from, fromMime) -> new SimpleFuture<>(from.getBytes());
        final TypeConverter<JSONObject, String> StringToJSONObject = (from, fromMime) -> new SimpleFuture<>(from).thenConvert(JSONObject::new);
        final TypeConverter<String, JSONObject> JSONObjectToString = (from, fromMime) -> new SimpleFuture<>(from).thenConvert(JSONObject::toString);
        final TypeConverter<String, byte[]> ByteArrayToString = (from, fromMime) -> new SimpleFuture<>(new String(from));

        Converters.addConverter(ByteBuffer.class, null, ByteBufferList.class, null, ByteBufferToByteBufferList);
        Converters.addConverter(String.class, null, byte[].class, "text/plain", StringToByteArray);
        Converters.addConverter(byte[].class, null, ByteBufferList.class, null, ByteArrayToByteBufferList);
        Converters.addConverter(ByteBufferList.class, null, byte[].class, null, ByteBufferListToByteArray);
        Converters.addConverter(ByteBufferList.class, null, ByteBuffer.class, null, ByteBufferListToByteBuffer);
        Converters.addConverter(ByteBufferList.class, "text/plain", String.class, null, ByteBufferListToString);
        Converters.addConverter(byte[].class, null, ByteBuffer.class, null, ByteArrayToByteBuffer);
        Converters.addConverter(String.class, "application/json", JSONObject.class, null, StringToJSONObject);
        Converters.addConverter(JSONObject.class, null, String.class, "application/json", JSONObjectToString);
        Converters.addConverter(byte[].class, "text/plain", String.class, null, ByteArrayToString);
    }
}
