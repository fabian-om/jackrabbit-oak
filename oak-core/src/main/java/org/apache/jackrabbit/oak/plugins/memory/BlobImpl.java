package org.apache.jackrabbit.oak.plugins.memory;

import java.io.InputStream;

import org.apache.jackrabbit.oak.api.Blob;
import org.apache.jackrabbit.oak.api.CoreValue;

public class BlobImpl implements Blob {
    private final CoreValue value;

    public BlobImpl(CoreValue value) {
        this.value = value;
    }

    @Override
    public InputStream getNewStream() {
        return value.getNewStream();
    }

    @Override
    public long length() {
        return value.length();
    }
}