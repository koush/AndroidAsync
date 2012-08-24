package com.koushikdutta.async;

import java.lang.reflect.Method;
import java.util.Hashtable;

public class TapCallback {
    static Hashtable<Class, Method> mTable = new Hashtable<Class, Method>();

    Method getTap() {
        Method found = mTable.get(getClass());
        if (found != null)
            return found;
        for (Method method : getClass().getMethods()) {
            if ("tap".equals(method.getName())) {
                mTable.put(getClass(), method);
                return method;
            }
        }
        return null;
    }
}
