package com.koushikdutta.async.future;

import com.koushikdutta.async.ByteBufferList;

import org.json.JSONObject;

import java.io.InvalidObjectException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
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
        public MultiTransformer(TypeConverter<T, F> converter, String converterMime) {
            this.converter = converter;
            this.converterMime = converterMime;
        }

        @Override
        protected void transform(MimedData<Future<F>> result) {
            // transform will only ever be called once,
            // so there's no risk of running the converter twice.
            final String mime = result.mime;

            final MultiFuture<T> converted = new MultiFuture<>();
            setComplete(new MimedData<Future<T>>(converted, new MimedType(null, mime).matches(converterMime).mime));

            result.data.setCallback(new FutureCallback<F>() {
                @Override
                public void onCompleted(Exception e, F data) {
                    converted.setComplete(converter.convert(data, mime));
                }
            });

            result.data.then((ThenFutureCallback<T, F>) from -> converter.convert(from, mime));
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
        public MimedType<T> matches(MimedType other) {
            // check the type
            if (!this.type.isAssignableFrom(other.type))
                return null;

            return matches(other.mime);
        }

        public String primary() {
            return mime.split("/")[0];
        }

        public String secondary() {
            return mime.split("/")[1];
        }

        // check if this mimed type is convertible to another mimed type
        public MimedType<T> matches(String mime) {
            String[] parts = mime.split("/");
            String[] myParts = this.mime.split("/");

            // mime conversion can only become more specific.
            if (!"*".equals(parts[0]) && !"*".equals(myParts[0]) && !parts[0].equals(myParts[0]))
                return null;

            if (!"*".equals(parts[1]) && !"*".equals(myParts[1]) && !parts[1].equals(myParts[1]))
                return null;

            String primary = !"*".equals(parts[0]) ? parts[0] : myParts[0];
            String secondary = !"*".equals(parts[1]) ? parts[1] : myParts[1];

            return new MimedType<>(type, primary + "/" + secondary);
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
            add(ret, get(mimedType));
            add(ret, get(new MimedType<>(mimedType.type, mimedType.primary() + "/*")));
            add(ret, get(new MimedType<>(mimedType.type, "*/*")));
            return ret;
        }
    }

    Converters<Object, Object> outputs = new Converters<>();

    synchronized protected <F, T> void addTypeConverter(Class<F> from, String fromMime, Class<T> to, String toMime, TypeConverter<T, F> typeConverter) {
        if (fromMime == null)
            fromMime = MIME_ALL;
        if (toMime == null)
            toMime = MIME_ALL;
        ((Converters<F, T>)outputs).ensure(new MimedType<>(from, fromMime)).put(new MimedType<>(to, toMime), new MultiTransformer<>(typeConverter, toMime));
    }

    private Converter() {
        addTypeConverter(ByteBuffer.class, null, ByteBufferList.class, null, ByteBufferToByteBufferList);
        addTypeConverter(String.class, null, byte[].class, null, StringToByteArray);
        addTypeConverter(byte[].class, null, ByteBufferList.class, null, ByteArrayToByteBufferList);
        addTypeConverter(byte[].class, null, ByteBuffer.class, null, ByteArrayToByteBuffer);
        addTypeConverter(String.class, "application/json", JSONObject.class, null, StringToJSONObject);
        addTypeConverter(JSONObject.class, null, String.class, "application/json", JSONObjectToString);
    }

    MultiFuture<R> future = new MultiFuture<>();
    String futureMime;
    protected Converter(Future future, String mime) {
        this();
        if (mime == null)
            mime = MIME_ALL;
        this.futureMime = mime;
        this.future.setComplete(future);
    }

    synchronized private <T> Future<T> to(Object value, Class<T> clazz, String mime) {
        if (clazz.isInstance(value))
            return new SimpleFuture<>((T) value);
        return to(value.getClass(), clazz, mime);
    }

    synchronized private <T> Future<T> to(Class fromClass, Class<T> clazz, String mime) {
        if (mime == null)
            mime = MIME_ALL;

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
    }

    public <T> Future<T> to(Class<T> clazz) {
        return to(clazz, null);
    }

    private <T> boolean search(MimedType<T> target, ArrayDeque<PathInfo> bestMatch, ArrayDeque<PathInfo> currentPath, MimedType currentSearch, HashSet<MimedType> searched) {
        if (target.matches(currentSearch) != null) {
            bestMatch.clear();
            bestMatch.addAll(currentPath);
            return true;
        }

        // the current path must have potential to be better than the best match
        // if best match is currently 4, the current path must be 2 to have
        // the chance of a new search being better.
        if (!bestMatch.isEmpty() && currentPath.size() > bestMatch.size() - 2)
            return false;

        // prevent reentrancy
        if (searched.contains(currentSearch))
            return false;
        if (searched.contains(new MimedType(currentSearch.type, currentSearch.primary() + "/*")))
            return false;
        if (searched.contains(new MimedType(currentSearch.type, "*/*")))
            return false;

        boolean found = false;
        searched.add(currentSearch);
        ConverterTransformers<Object, Object> converterTransformers = outputs.getAll(currentSearch);
        for (MimedType candidate: converterTransformers.keySet()) {
            // this simulates the mime results of a transform
            MimedType newSearch = candidate.matches(currentSearch.mime);

            PathInfo path = new PathInfo();
            path.transformer = converterTransformers.get(candidate);
            path.mime = newSearch.mime;
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
    public <T> Future<T> to(final Class<T> clazz, final String mime) {
        return future.then(from -> to(from, clazz ,mime));
    }

    private TypeConverter<byte[], String> StringToByteArray = (from, fromMime) -> new SimpleFuture<>(from.getBytes());

    private TypeConverter<ByteBufferList, byte[]> ByteArrayToByteBufferList = (from, fromMime) -> new SimpleFuture<>(new ByteBufferList(from));

    private TypeConverter<ByteBuffer, byte[]> ByteArrayToByteBuffer = (from, fromMime) -> new SimpleFuture<>(ByteBufferList.deepCopy(ByteBuffer.wrap(from)));

    private TypeConverter<ByteBufferList, ByteBuffer> ByteBufferToByteBufferList = (from, fromMime) -> new SimpleFuture<>(new ByteBufferList(ByteBufferList.deepCopy(from)));

    private TypeConverter<JSONObject, String> StringToJSONObject = (from, fromMime) -> new SimpleFuture<>(from).thenConvert(JSONObject::new);

    private TypeConverter<String, JSONObject> JSONObjectToString = (from, fromMime) -> new SimpleFuture<>(from).thenConvert(JSONObject::toString);
}
