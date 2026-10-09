package app.anibrave;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class EngineHttpTest {
    static void rejects(String headers, byte[] data) throws Exception {
        try { EngineHttp.body(new ByteArrayInputStream(data), headers, 32); throw new AssertionError("Accepted invalid response"); }
        catch (IOException expected) {}
    }
    public static void main(String[] args) throws Exception {
        InputStream keptOpen = new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)) {
            public synchronized int read(byte[] b, int off, int len) {
                if (available() == 0) throw new AssertionError("Waited for EOF on persistent connection");
                return super.read(b, off, Math.min(len, 1));
            }
        };
        if (!EngineHttp.body(keptOpen, "HTTP/1.1 200 OK\r\ncontent-LENGTH:2\r\n\r\n", 32).equals("{}")) throw new AssertionError();
        rejects("Content-Length: 4", new byte[2]);
        rejects("Content-Length: 1000", new byte[0]);
        rejects("Content-Length: invalid", new byte[0]);
        rejects("HTTP/1.1 200 OK", new byte[0]);
        System.out.println("5 persistent HTTP response checks passed");
    }
}
