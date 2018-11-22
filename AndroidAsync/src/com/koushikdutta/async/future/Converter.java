package com.koushikdutta.async.future;

import com.koushikdutta.async.ByteBufferList;

import org.json.JSONObject;

import java.io.InvalidObjectException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;

public class Converter<R> {
    final public static <T> Converter<T> convert(Future<T> future, String mime) {
        return new Converter<>(future, mime);
    }

    final public static <T> Converter<T> convert(Future<T> future) {
        return convert(future, null);
    }

    static class MultiTransformer<T, F> extends MultiTransformFuture<T, Future<F>> {
        TypeConverter<T, F> converter;
        public MultiTransformer(TypeConverter<T, F> converter) {
            this.converter = converter;
        }

        @Override
        protected void transform(Future<F> result) {
            // transform will only ever be called once,
            // so there's no risk of running the converter twice.
            result.setCallback(new FutureCallback<F>() {
                @Override
                public void onCompleted(Exception e, F result) {
                    setComplete(converter.convert(result));
                }
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
        ((Converters<F, T>)outputs).ensure(new MimedType<>(from, fromMime)).put(new MimedType<>(to, toMime), new MultiTransformer<>(typeConverter));
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
            return new SimpleFuture<>((T)value);

        if (mime == null)
            mime = MIME_ALL;

        MimedType<T> target = new MimedType<>(clazz, mime);
        ArrayDeque<MultiTransformer<Object, Object>> bestMatch = new ArrayDeque<>();
        ArrayDeque<MultiTransformer<Object, Object>> currentPath = new ArrayDeque<>();
        if (search(target, bestMatch, currentPath, new MimedType(value.getClass(), futureMime), new HashSet<MimedType>())) {
            SimpleFuture<T> ret = new SimpleFuture<>();
            MultiTransformer<Object, Object> current = bestMatch.removeLast();
            ret.setComplete((MultiTransformer<T, Object>)current);
            while (!bestMatch.isEmpty()) {
                MultiTransformer<Object, Object> next = bestMatch.removeLast();
                current.setComplete(next);
                current = next;
            }
            current.setComplete(future);
            return ret;
        }

        return new SimpleFuture<>(new InvalidObjectException("unable to find converter"));
    }

    public <T> Future<T> to(Class<T> clazz) {
        return to(clazz, null);
    }

    private <T> boolean search(MimedType<T> target, ArrayDeque<MultiTransformer<Object, Object>> bestMatch, ArrayDeque<MultiTransformer<Object, Object>> currentPath, MimedType currentSearch, HashSet<MimedType> searched) {
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

            currentPath.addLast(converterTransformers.get(candidate));
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
        return ((Future<R>)future).then(new TransformFuture<T, R>() {
            @Override
            protected void transform(R result) {
                Future<T> converted = to(result, clazz, mime);
                setComplete(converted);
            }
        });
    }

    public interface TypeConverter<T, F> {
        Future<T> convert(F from);
    }

    private TypeConverter<byte[], String> StringToByteArray = new TypeConverter<byte[], String>() {
        @Override
        public SimpleFuture<byte[]> convert(String from) {
            return new SimpleFuture<>(from.getBytes());
        }
    };

    private TypeConverter<ByteBufferList, byte[]> ByteArrayToByteBufferList = new TypeConverter<ByteBufferList, byte[]>() {
        @Override
        public SimpleFuture<ByteBufferList> convert(byte[] from) {
            return new SimpleFuture<>(new ByteBufferList(from));
        }
    };

    private TypeConverter<ByteBuffer, byte[]> ByteArrayToByteBuffer = new TypeConverter<ByteBuffer, byte[]>() {
        @Override
        public SimpleFuture<ByteBuffer> convert(byte[] from) {
            return new SimpleFuture<>(ByteBufferList.deepCopy(ByteBuffer.wrap(from)));
        }
    };

    private TypeConverter<ByteBufferList, ByteBuffer> ByteBufferToByteBufferList = new TypeConverter<ByteBufferList, ByteBuffer>() {
        @Override
        public SimpleFuture<ByteBufferList> convert(ByteBuffer from) {
            return new SimpleFuture<>(new ByteBufferList(ByteBufferList.deepCopy(from)));
        }
    };

    private TypeConverter<JSONObject, String> StringToJSONObject = new TypeConverter<JSONObject, String>() {
        @Override
        public SimpleFuture<JSONObject> convert(String from) {
            return new TransformFuture<JSONObject, String>(from) {
                @Override
                protected void transform(String result) throws Exception {
                    setComplete(new JSONObject(result));
                }
            };
        }
    };

    private TypeConverter<String, JSONObject> JSONObjectToString = new TypeConverter<String, JSONObject>() {
        @Override
        public SimpleFuture<String> convert(JSONObject from) {
            return new TransformFuture<String, JSONObject>(from) {
                @Override
                protected void transform(JSONObject result) throws Exception {
                    setComplete(result.toString());
                }
            };
        }
    };


}
