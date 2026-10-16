package com.yingliu.media.storage;

import java.io.InputStream;

/**
 * Abstraction for media file storage. Supports both local-disk and Alibaba Cloud OSS backends.
 */
public interface MediaStorage {

    /**
     * Store a file and return the access URL.
     *
     * @param key         object key (e.g. "ab/sha256hash.mp4")
     * @param data        file content stream
     * @param size        content length in bytes (-1 if unknown)
     * @param contentType MIME type (e.g. "video/mp4")
     * @return URL to access the stored file
     */
    String store(String key, InputStream data, long size, String contentType);

    /**
     * Get the access URL for an existing object.
     *
     * @param key object key
     * @return URL string
     */
    String getUrl(String key);

    /**
     * Check whether an object exists.
     *
     * @param key object key
     * @return true if the object exists
     */
    boolean exists(String key);

    /**
     * Delete an object. No-op if the object does not exist.
     *
     * @param key object key
     */
    void delete(String key);
}
