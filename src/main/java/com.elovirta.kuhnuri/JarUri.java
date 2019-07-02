package com.elovirta.kuhnuri;

import java.net.URI;

class JarUri {
    public final URI url;
    // FIXME should be Optional
    public final String entry;

    private JarUri(URI url, String entry) {
        this.url = url;
        this.entry = entry;
    }

    public static JarUri of(final URI in) {
        if (!in.getScheme().equals("jar")) {
            throw new IllegalArgumentException(in.toString());
        }
        final String s = in.toString();
        final int i = s.indexOf("!/");
        final String entry = s.substring(i + 2);
        return new JarUri(URI.create(s.substring(4, i)), entry.isEmpty() ? null : entry);
    }
}
