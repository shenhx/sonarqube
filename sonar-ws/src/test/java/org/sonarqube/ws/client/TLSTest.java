package org.sonarqube.ws.client;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.NCSARequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class TLSTest {

  private static Server server;

  @BeforeClass
  public static void startServer() throws Exception {
    // Setup Threadpool
    QueuedThreadPool threadPool = new QueuedThreadPool();
    threadPool.setMaxThreads(500);

    server = new Server(threadPool);

    // HTTP Configuration
    HttpConfiguration http_config = new HttpConfiguration();
    http_config.setSecureScheme("https");
    http_config.setSecurePort(8443);
    http_config.setSendServerVersion(true);
    http_config.setSendDateHeader(false);

    // Handler Structure
    ResourceHandler handler = new ResourceHandler();
    handler.setResourceBase(TLSTest.class.getResource("/static").toString());

    HandlerCollection handlers = new HandlerCollection();
    handlers.setHandlers(new Handler[] {handler, new DefaultHandler()});
    server.setHandler(handlers);

    ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(http_config));
    http.setPort(8080);
    server.addConnector(http);

    Path serverKeystore = Paths.get("src/test/resources/serverkeystore").toAbsolutePath();
    String keystorePassword = "keystoreserver";
    Path truststoreCa = Paths.get("src/test/resources/catruststore").toAbsolutePath();
    String truststorePassword = "truststoreca";

    // SSL Context Factory
    SslContextFactory sslContextFactory = new SslContextFactory();
    sslContextFactory.setKeyStorePath(serverKeystore.toString());
    sslContextFactory.setKeyStorePassword(keystorePassword);
    sslContextFactory.setKeyManagerPassword("secretserver");
    sslContextFactory.setTrustStorePath(truststoreCa.toString());
    sslContextFactory.setTrustStorePassword(truststorePassword);
    sslContextFactory.setNeedClientAuth(true);
    sslContextFactory.setExcludeCipherSuites("SSL_RSA_WITH_DES_CBC_SHA",
      "SSL_DHE_RSA_WITH_DES_CBC_SHA", "SSL_DHE_DSS_WITH_DES_CBC_SHA",
      "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
      "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
      "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
      "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");

    // SSL HTTP Configuration
    HttpConfiguration https_config = new HttpConfiguration(http_config);

    // SSL Connector
    ServerConnector sslConnector = new ServerConnector(server,
      new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
      new HttpConnectionFactory(https_config));
    sslConnector.setPort(8443);
    server.addConnector(sslConnector);

    // === jetty-requestlog.xml ===
    NCSARequestLog requestLog = new NCSARequestLog();
    requestLog.setFilename(Paths.get("target/yyyy_mm_dd.request.log").toAbsolutePath().toString());
    requestLog.setFilenameDateFormat("yyyy_MM_dd");
    requestLog.setRetainDays(90);
    requestLog.setAppend(true);
    requestLog.setExtended(true);
    requestLog.setLogCookies(false);
    requestLog.setLogTimeZone("GMT");
    RequestLogHandler requestLogHandler = new RequestLogHandler();
    requestLogHandler.setRequestLog(requestLog);
    handlers.addHandler(requestLogHandler);

    server.setStopAtShutdown(true);
    server.start();
  }

  @Test
  public void testConnection() {
    Path clientTruststore = Paths.get("src/test/resources/clienttruststore").toAbsolutePath();
    String truststorePassword = "truststoreclient";
    System.setProperty("javax.net.ssl.trustStore", clientTruststore.toString());
    System.setProperty("javax.net.ssl.trustStorePassword", truststorePassword);

    Path clientKeystore = Paths.get("src/test/resources/clientkeystore").toAbsolutePath();
    String keystorePassword = "secret";
    System.setProperty("javax.net.ssl.keyStore", clientKeystore.toString());
    System.setProperty("javax.net.ssl.keyStorePassword", keystorePassword);
    HttpConnector connector = HttpConnector.newBuilder()
      .url("https://localhost:8443")
      .build();

    WsResponse wsResponse = connector.call(new GetRequest("/hello.html"));
    wsResponse.failIfNotSuccessful();
  }

  @AfterClass
  public static void stopServer() throws Exception {
    server.stop();
  }

}
