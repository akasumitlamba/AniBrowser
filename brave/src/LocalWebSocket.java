package app.anibrave;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Base64;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** WebSocket over Chromium's UID-protected abstract Unix socket; no TCP listener. */
final class LocalWebSocket implements Closeable {
    private final LocalSocket socket;
    private final InputStream in;
    private final OutputStream out;
    private final SecureRandom random = new SecureRandom();
    private static final int MAX = 4 * 1024 * 1024;

    static LocalSocket connect() throws IOException {
        LocalSocket result = new LocalSocket();
        try {
            result.connect(new LocalSocketAddress("anibrave_devtools_remote", LocalSocketAddress.Namespace.ABSTRACT));
            result.setSoTimeout(6000);
            return result;
        } catch (IOException e) { result.close(); throw e; }
    }

    static String get(String path) throws IOException {
        try (LocalSocket s = connect()) {
            OutputStream out = s.getOutputStream();
            out.write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            String headers = headers(s.getInputStream());
            if (!headers.startsWith("HTTP/1.1 200")) throw new IOException("Local engine unavailable");
            return EngineHttp.body(s.getInputStream(), headers, MAX);
        }
    }

    LocalWebSocket(String path) throws Exception {
        socket = connect(); in = socket.getInputStream(); out = socket.getOutputStream();
        try {
            byte[] nonce = new byte[16]; random.nextBytes(nonce);
            String key = Base64.encodeToString(nonce, Base64.NO_WRAP);
            out.write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: " + key + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            String response = headers(in);
            String accept = Base64.encodeToString(MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)), Base64.NO_WRAP);
            if (!response.startsWith("HTTP/1.1 101") || !response.contains(accept)) throw new IOException("Invalid local handshake");
            socket.setSoTimeout(0);
        } catch (Exception e) { socket.close(); throw e; }
    }

    private static String headers(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(); int state = 0;
        while (buffer.size() < 16384) {
            int b = in.read(); if (b < 0) throw new EOFException(); buffer.write(b);
            state = b == (state == 0 || state == 2 ? '\r' : '\n') ? state + 1 : (b == '\r' ? 1 : 0);
            if (state == 4) return buffer.toString("US-ASCII");
        }
        throw new IOException("Oversized HTTP headers");
    }

    synchronized void send(String value) throws IOException { frame(1, value.getBytes(StandardCharsets.UTF_8)); }
    private synchronized void frame(int opcode, byte[] value) throws IOException {
        if (value.length > MAX) throw new IOException("Oversized frame");
        out.write(0x80 | opcode);
        if (value.length < 126) out.write(0x80 | value.length);
        else if (value.length < 65536) { out.write(0x80 | 126); out.write(value.length >> 8); out.write(value.length); }
        else { out.write(0x80 | 127); for (int i = 7; i >= 0; i--) out.write((int) (((long) value.length >> (8 * i)) & 255)); }
        byte[] mask = new byte[4]; random.nextBytes(mask); out.write(mask);
        byte[] masked = value.clone(); for (int i = 0; i < masked.length; i++) masked[i] ^= mask[i % 4];
        out.write(masked); out.flush();
    }
    String read() throws IOException {
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        while (true) {
            int first = readByte(), second = readByte(); int opcode = first & 15;
            long length = second & 127;
            if (length == 126) length = (readByte() << 8) | readByte();
            else if (length == 127) { length = 0; for (int i = 0; i < 8; i++) length = (length << 8) | readByte(); }
            if (length < 0 || length > MAX || text.size() + length > MAX) throw new IOException("Oversized WebSocket message");
            byte[] mask = (second & 128) != 0 ? bytes(4) : null;
            byte[] payload = bytes((int) length);
            if (mask != null) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
            if (opcode == 8) throw new EOFException();
            if (opcode == 9) { frame(10, payload); continue; }
            if (opcode == 10) continue;
            if (opcode != 1 && opcode != 0) throw new IOException("Unexpected message opcode");
            text.write(payload);
            if ((first & 128) != 0) return text.toString("UTF-8");
        }
    }
    private int readByte() throws IOException { int b = in.read(); if (b < 0) throw new EOFException(); return b; }
    private byte[] bytes(int count) throws IOException {
        byte[] result = new byte[count]; int off = 0;
        while (off < count) { int n = in.read(result, off, count - off); if (n < 0) throw new EOFException(); off += n; }
        return result;
    }
    public void close() throws IOException { socket.close(); }
}
