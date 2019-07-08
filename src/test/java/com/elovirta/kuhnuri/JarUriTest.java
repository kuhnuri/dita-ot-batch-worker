package com.elovirta.kuhnuri;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;

public class JarUriTest {
    @Test
    public void testBoth() {
        var uri = JarUri.of(URI.create("jar:s3://foo/bar!/baz"));
        assertEquals(URI.create("s3://foo/bar"), uri.url);
        assertEquals("baz", uri.entry);
    }

    @Test
    public void testDir() {
        var uri = JarUri.of(URI.create("jar:s3://foo/bar!/baz/"));
        assertEquals(URI.create("s3://foo/bar"), uri.url);
        assertEquals("baz/", uri.entry);
    }

    @Test
    public void testOnlyUrl() {
        var uri = JarUri.of(URI.create("jar:s3://foo/bar!/"));
        assertEquals(URI.create("s3://foo/bar"), uri.url);
        assertEquals(null, uri.entry);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingSeparator() {
        JarUri.of(URI.create("jar:s3://foo/bar"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidUrl() {
        JarUri.of(URI.create("jar://foo/bar!/"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidScheme() {
        JarUri.of(URI.create("s3://foo/bar!/"));
    }
}