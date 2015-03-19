package com.koushikdutta.async.util;

import android.content.Context;

/**
 * Provides a {@link android.content.Context} to check wifi status.
 */
public class ContextProvider {

    private static Context context;

    private ContextProvider() {
    }

    public static void createContextProvider(Context context) {
        if (ContextProvider.context == null) {
            ContextProvider.context = context;
        }
    }

    public static Context getContext() throws NoContextException {
        if (ContextProvider.context == null) {
            throw new NoContextException();
        }
        return ContextProvider.context;
    }

}
