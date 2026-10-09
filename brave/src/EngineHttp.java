package app.anibrave;

import java.io.*;
import java.util.Locale;

/** Chromium keeps HTTP connections open even after a Connection: close request. */
final class EngineHttp {
    static String body(InputStream in, String headers, int maximum) throws IOException {
        int length = -1;
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                try { length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim()); }
                catch (NumberFormatException e) { throw new IOException("Invalid local response length", e); }
            }
        }
        if (length < 0 || length > maximum) throw new IOException("Invalid local response length");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] bytes = new byte[4096];
        while (body.size() < length) {
            int n = in.read(bytes, 0, Math.min(bytes.length, length - body.size()));
            if (n < 0) throw new EOFException("Incomplete local response");
            body.write(bytes, 0, n);
        }
        return body.toString("UTF-8");
    }
}
