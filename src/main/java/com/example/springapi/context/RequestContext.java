package com.example.springapi.context;

public final class RequestContext {

    private RequestContext() {
    }

    //since Java 21 we can use ScopeValues
    public static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();
}
