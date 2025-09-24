package com.datadobi.s3test.util;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.cert.X509Certificate;

public final class TLS {
    private TLS() {
    }

    public static X509TrustManager createLenientTrustManager() {
        return new DummyX509TrustManager();
    }

    public static SSLSocketFactory createLenientSSLSocketFactory(X509TrustManager trustManager) throws IOException {
        // Trust all certificates
        SSLContext sslContext;
        try {
            // Install the all-trusting trust manager
            sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, new TrustManager[]{trustManager}, new java.security.SecureRandom());
        } catch (Exception e) {
            throw new IOException("Could not initialize SSLcontext", e);
        }
        SSLSocketFactory socketFactory = sslContext.getSocketFactory();
        return new EnableAllCiphersSSLSocketFactory(socketFactory);
    }

    /**
     * A trust manager that does not validate certificate chains.
     */
    private static class DummyX509TrustManager implements X509TrustManager {
        private static final X509Certificate[] ACCEPTED_ISSUERS = new X509Certificate[0];

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return ACCEPTED_ISSUERS;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] certs, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] certs, String authType) {
        }
    }

    /**
     * An SSL socket factory that enables all supported ciphers on each SSL socket it creates.
     * This is necessary to ensure legacy ciphers (e.g. RC4) are enabled which we need in order
     * to connect to older file servers.
     */
    private static class EnableAllCiphersSSLSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory socketFactory;

        public EnableAllCiphersSSLSocketFactory(SSLSocketFactory socketFactory) {
            this.socketFactory = socketFactory;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            if (System.getProperty("jdk.tls.client.cipherSuites") != null) {
                // If the client set of cipher suites was specified via the standard system property, then
                // stick to that set by returning the defaults.
                return socketFactory.getDefaultCipherSuites();
            } else {
                // Otherwise use all available cipher suites instead.
                return socketFactory.getSupportedCipherSuites();
            }
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return socketFactory.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return configure(socketFactory.createSocket(s, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return configure(socketFactory.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return configure(socketFactory.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return configure(socketFactory.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress host, int port, InetAddress localHost, int localPort) throws IOException {
            return configure(socketFactory.createSocket(host, port, localHost, localPort));
        }

        private Socket configure(Socket s) {
            if (s instanceof SSLSocket) {
                SSLSocket ssl = (SSLSocket) s;
                ssl.setEnabledCipherSuites(getDefaultCipherSuites());
            }
            return s;
        }
    }
}
