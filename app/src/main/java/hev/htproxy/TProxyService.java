package hev.htproxy;

/**
 * JNI bridge to hev-socks5-tunnel. The native library registers its methods on exactly
 * this class name (hev.htproxy.TProxyService), so the name must not change.
 */
public class TProxyService {
    static {
        System.loadLibrary("hev-socks5-tunnel");
    }

    public static native boolean TProxyStartService(String configPath, int fd);

    public static native boolean TProxyStopService();

    public static native boolean TProxyIsRunning();

    public static native long[] TProxyGetStats();
}
