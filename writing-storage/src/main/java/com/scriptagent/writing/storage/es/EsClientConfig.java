/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.es;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 7 客户端配置.
 *
 * <p>从 {@code application.yaml} 读取节点地址（{@code elasticsearch.uris}，逗号分隔多节点）， 可选 basic auth（{@code
 * elasticsearch.username} / {@code elasticsearch.password}，为空则跳过）. Bean 销毁时自动关闭底层 HTTP 连接池.
 */
@Configuration
public class EsClientConfig {

  @Value("${elasticsearch.uris}")
  private String uris;

  @Value("${elasticsearch.username:}")
  private String username;

  @Value("${elasticsearch.password:}")
  private String password;

  /**
   * 构造 {@link RestHighLevelClient}. 节点地址支持逗号分隔的多 URI 形式（如 {@code
   * http://node1:9200,http://node2:9200}）.
   */
  @Bean(destroyMethod = "close")
  public RestHighLevelClient restHighLevelClient() {
    HttpHost[] hosts = parseUris(uris);
    RestClientBuilder builder = RestClient.builder(hosts);
    if (!username.isEmpty()) {
      CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(
          AuthScope.ANY, new UsernamePasswordCredentials(username, password));
      builder.setHttpClientConfigCallback(
          httpClientBuilder ->
              httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
    }
    return new RestHighLevelClient(builder);
  }

  private static HttpHost[] parseUris(String uriList) {
    List<HttpHost> hosts = new ArrayList<>();
    for (String raw : uriList.split(",")) {
      String trimmed = raw.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      URI uri = URI.create(trimmed);
      int port = uri.getPort() > 0 ? uri.getPort() : 9200;
      hosts.add(new HttpHost(uri.getHost(), port, uri.getScheme()));
    }
    if (hosts.isEmpty()) {
      throw new IllegalStateException(
          "elasticsearch.uris 配置为空，至少需要一个节点地址，例如 http://127.0.0.1:9200");
    }
    return hosts.toArray(new HttpHost[0]);
  }

  /** 测试用：返回节点数组，便于单元测试独立校验 URI 解析逻辑. */
  static HttpHost[] parseUrisForTest(String uriList) {
    return parseUris(uriList);
  }

  /** 仅用于 Bean 配置测试时校验依赖. */
  @SuppressWarnings("unused")
  private static List<String> supportedSchemes() {
    return Arrays.asList("http", "https");
  }
}
