package org.fortishop.productinventoryservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.fortishop.productinventoryservice.Repository.InventoryRepository;
import org.fortishop.productinventoryservice.Repository.ProductRepository;
import org.fortishop.productinventoryservice.Repository.ProductSearchRepository;
import org.fortishop.productinventoryservice.domain.Inventory;
import org.fortishop.productinventoryservice.domain.Product;
import org.fortishop.productinventoryservice.domain.ProductDocument;
import org.fortishop.productinventoryservice.dto.request.InventoryRequest;
import org.fortishop.productinventoryservice.dto.request.ProductRequest;
import org.fortishop.productinventoryservice.dto.response.InventoryResponse;
import org.fortishop.productinventoryservice.dto.response.ProductResponse;
import org.fortishop.productinventoryservice.service.TestInventoryHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.location=classpath:/application-test.yml",
                "spring.profiles.active=test"
        }
)
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ProductInventoryServiceIntegrationTests {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    InventoryRepository inventoryRepository;

    @Autowired
    ProductSearchRepository productSearchRepository;

    @Autowired
    ProductSearchRepository searchRepository;

    @Autowired
    private TestInventoryHelper testInventoryHelper;

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("fortishop")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2.1")
            .withExposedPorts(6379);

    @Container
    static GenericContainer<?> zookeeper = new GenericContainer<>(DockerImageName.parse("bitnami/zookeeper:3.8.1"))
            .withEnv("ALLOW_ANONYMOUS_LOGIN", "yes")
            .withExposedPorts(2181)
            .withNetwork(Network.SHARED)
            .withNetworkAliases("zookeeper");

    @Container
    static GenericContainer<?> kafka = new GenericContainer<>(DockerImageName.parse("bitnami/kafka:3.6.0"))
            .withExposedPorts(9092, 9093)
            .withNetwork(Network.SHARED)
            .withNetworkAliases("kafka")
            .withCreateContainerCmdModifier(cmd -> {
                cmd.withHostName("kafka");
                cmd.withHostConfig(
                        Objects.requireNonNull(cmd.getHostConfig())
                                .withPortBindings(
                                        new PortBinding(Ports.Binding.bindPort(9092), new ExposedPort(9092)),
                                        new PortBinding(Ports.Binding.bindPort(9093), new ExposedPort(9093))
                                )
                );
            })
            .withEnv("KAFKA_BROKER_ID", "1")
            .withEnv("ALLOW_PLAINTEXT_LISTENER", "yes")
            .withEnv("KAFKA_CFG_ZOOKEEPER_CONNECT", "zookeeper:2181")
            .withEnv("KAFKA_CFG_LISTENERS", "PLAINTEXT://0.0.0.0:9092,EXTERNAL://0.0.0.0:9093")
            .withEnv("KAFKA_CFG_ADVERTISED_LISTENERS",
                    "PLAINTEXT://kafka:9092,EXTERNAL://localhost:9093")
            .withEnv("KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP",
                    "PLAINTEXT:PLAINTEXT,EXTERNAL:PLAINTEXT")
            .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT")
            .withEnv("KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE", "true")
            .waitingFor(Wait.forLogMessage(".*\\[KafkaServer id=\\d+] started.*\\n", 1));

    @Container
    static GenericContainer<?> kafkaUi = new GenericContainer<>(DockerImageName.parse("provectuslabs/kafka-ui:latest"))
            .withExposedPorts(8080)
            .withEnv("KAFKA_CLUSTERS_0_NAME", "fortishop-cluster")
            .withEnv("KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS", "PLAINTEXT://kafka:9092")
            .withEnv("KAFKA_CLUSTERS_0_ZOOKEEPER", "zookeeper:2181")
            .withNetwork(Network.SHARED)
            .withNetworkAliases("kafka-ui");

    @Container
    static GenericContainer<?> elasticsearch = new GenericContainer<>(
            "docker.elastic.co/elasticsearch/elasticsearch:9.0.0")
            .withExposedPorts(9200)
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false") // 인증 비활성화 (테스트용)
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .waitingFor(Wait.forHttp("/").forStatusCode(200));

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        mysql.start();
        redis.start();
        zookeeper.start();
        kafka.start();
        kafkaUi.start();
        elasticsearch.start();

        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", () -> kafka.getHost() + ":" + kafka.getMappedPort(9093));
        registry.add("spring.elasticsearch.uris", () ->
                "http://" + elasticsearch.getHost() + ":" + elasticsearch.getMappedPort(9200));
    }

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/products";
    }

    private static boolean topicCreated = false;

    @BeforeAll
    static void printKafkaUiUrl() throws Exception {
        System.out.println("Kafka UI is available at: http://" + kafkaUi.getHost() + ":" + kafkaUi.getMappedPort(8080));
        if (!topicCreated) {
            String bootstrap = kafka.getHost() + ":" + kafka.getMappedPort(9093);
            createTopicIfNotExists("order.created", bootstrap);
            createTopicIfNotExists("payment.failed", bootstrap);
            topicCreated = true;
        }
    }

    @BeforeEach
    void cleanDatabase() {
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        searchRepository.deleteAll();
    }

    @Test
    @DisplayName("상품 등록 및 조회에 성공한다")
    void createAndGetProduct_success() {
        ProductRequest request = new ProductRequest("상품명", "설명", BigDecimal.valueOf(10000), "카테고리", "image.jpg", true);

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-member-role", "ROLE_ADMIN");
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ProductRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ProductResponse> created = restTemplate.postForEntity(getBaseUrl(), entity,
                ProductResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long productId = Objects.requireNonNull(created.getBody()).getId();

        ResponseEntity<ProductResponse> fetched = restTemplate.getForEntity(getBaseUrl() + "/" + productId,
                ProductResponse.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(fetched.getBody()).getName()).isEqualTo("상품명");
    }

    @Test
    @DisplayName("재고 수동 설정 및 조회에 성공한다")
    void setAndGetInventory_success() {
        Product saved = productRepository.save(Product.builder()
                .name("상품")
                .description("desc")
                .price(BigDecimal.valueOf(1000))
                .category("cat")
                .imageUrl("img")
                .isActive(true)
                .build());

        productSearchRepository.save(
                ProductDocument.builder()
                        .id(saved.getId())
                        .name(saved.getName())
                        .description(saved.getDescription())
                        .price(saved.getPrice())
                        .category(saved.getCategory())
                        .quantity(0)
                        .build()
        );

        InventoryRequest req = new InventoryRequest(50);
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-member-role", "ROLE_ADMIN");
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<InventoryRequest> entity = new HttpEntity<>(req, headers);

        ResponseEntity<InventoryResponse> setRes = restTemplate.exchange(
                "http://localhost:" + port + "/api/inventory/" + saved.getId(),
                HttpMethod.PATCH,
                entity,
                InventoryResponse.class
        );

        assertThat(setRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(setRes.getBody()).getQuantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("존재하지 않는 상품 조회 시 404 반환")
    void getProduct_notFound() {
        ResponseEntity<String> response = restTemplate.getForEntity(getBaseUrl() + "/9999", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 상품 재고 조회 시 404 반환")
    void getInventory_notFound() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/inventory/9999", String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("인기 상품 목록을 조회한다")
    void getPopularProducts_success() {
        Product p1 = productRepository.save(Product.builder()
                .name("P1").description("d1").price(BigDecimal.valueOf(1000)).category("c").imageUrl("img1")
                .isActive(true).build());
        Product p2 = productRepository.save(Product.builder()
                .name("P2").description("d2").price(BigDecimal.valueOf(2000)).category("c").imageUrl("img2")
                .isActive(true).build());

        restTemplate.getForEntity(getBaseUrl() + "/" + p1.getId(), ProductResponse.class);
        restTemplate.getForEntity(getBaseUrl() + "/" + p1.getId(), ProductResponse.class);
        restTemplate.getForEntity(getBaseUrl() + "/" + p2.getId(), ProductResponse.class);

        ResponseEntity<ProductResponse[]> res = restTemplate.getForEntity(
                getBaseUrl() + "/popular?limit=2", ProductResponse[].class
        );

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).hasSize(2);
        assertThat(res.getBody()[0].getId()).isEqualTo(p1.getId());
    }

    @Test
    @DisplayName("상품 목록 조회 시 페이징이 적용된다")
    void getProducts_paging_success() {
        for (int i = 1; i <= 15; i++) {
            productRepository.save(Product.builder()
                    .name("상품" + i).description("desc").price(BigDecimal.valueOf(1000))
                    .category("cat").imageUrl("img").isActive(true).build());
        }

        ResponseEntity<String> res = restTemplate.getForEntity(
                getBaseUrl() + "?page=0&size=10", String.class
        );

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("상품1");
    }

    @Test
    @DisplayName("Kafka 이벤트 기반 재고 차감 처리 - 성공")
    void handleOrderCreated_event_success() throws Exception {
        Product product = productRepository.save(Product.builder()
                .name("Test Product").description("desc").price(BigDecimal.valueOf(1000))
                .category("cat").imageUrl("img").isActive(true).build());

        productSearchRepository.save(ProductDocument.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .quantity(10)
                .category(product.getCategory())
                .build());

        inventoryRepository.save(Inventory.builder().productId(product.getId()).quantity(10).build());

        Map<String, Object> orderEvent = Map.of(
                "orderId", 1,
                "memberId", 1,
                "totalPrice", 1000,
                "address", "서울특별시",
                "createdAt", LocalDateTime.now().toString(),
                "traceId", UUID.randomUUID().toString(),
                "items", List.of(Map.of("productId", product.getId(), "quantity", 2, "price", 1000))
        );

        String bootstrapServers = kafka.getHost() + ":" + kafka.getMappedPort(9093);

        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            await()
                    .atMost(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(500))
                    .until(() -> admin.listTopics().names().get().contains("order.created"));
        }

        KafkaProducer<String, Object> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonSerializer.class
        ));

        producer.send(new ProducerRecord<>("order.created", product.getId().toString(), orderEvent));
        producer.flush();
        producer.close();

        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    Inventory after = testInventoryHelper.findByProductIdWithLock(product.getId());
                    assertThat(after.getQuantity()).isEqualTo(8);
                });
    }

    @Test
    @Transactional
    @DisplayName("Redisson 락 - 동시에 여러 쓰레드가 접근할 경우 하나만 성공한다")
    void concurrentStockDecrease_locking() throws Exception {
        Product product = productRepository.save(Product.builder()
                .name("LockTest").description("desc").price(BigDecimal.valueOf(1000))
                .category("cat").imageUrl("img").isActive(true).build());

        productSearchRepository.save(ProductDocument.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .quantity(5)
                .category(product.getCategory())
                .build());

        inventoryRepository.save(Inventory.builder().productId(product.getId()).quantity(5).build());

        Runnable task = () -> {
            Map<String, Object> orderEvent = Map.of(
                    "orderId", new Random().nextInt(10000),
                    "memberId", 1,
                    "totalPrice", 1000,
                    "address", "서울특별시",
                    "createdAt", LocalDateTime.now().toString(),
                    "traceId", UUID.randomUUID().toString(),
                    "items", List.of(Map.of("productId", product.getId(), "quantity", 5, "price", 1000))
            );

            KafkaProducer<String, Object> producer = new KafkaProducer<>(Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                    kafka.getHost() + ":" + kafka.getMappedPort(9093),
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                    org.springframework.kafka.support.serializer.JsonSerializer.class
            ));
            producer.send(new ProducerRecord<>("order.created", product.getId().toString(), orderEvent));
            producer.flush();
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();

        t1.join();
        t2.join();

        Thread.sleep(3000);

        Inventory inventory = inventoryRepository.findByProductIdForUpdate(product.getId()).orElseThrow();
        assertThat(inventory.getQuantity()).isBetween(0, 5);
    }

    @Test
    @DisplayName("Elasticsearch 상품 검색 성공")
    void searchProducts_elasticsearch_success() throws Exception {
        // given
        Product p1 = productRepository.save(Product.builder()
                .name("Apple iPhone").description("Smartphone").price(BigDecimal.valueOf(1000))
                .category("Electronics").imageUrl("iphone.jpg").isActive(true).build());
        Product p2 = productRepository.save(Product.builder()
                .name("Banana Phone").description("Gag gift").price(BigDecimal.valueOf(10))
                .category("Fun").imageUrl("banana.jpg").isActive(true).build());

        ProductDocument doc1 = ProductDocument.builder()
                .id(p1.getId()).name(p1.getName()).description(p1.getDescription())
                .price(p1.getPrice()).category(p1.getCategory()).build();

        ProductDocument doc2 = ProductDocument.builder()
                .id(p2.getId()).name(p2.getName()).description(p2.getDescription())
                .price(p2.getPrice()).category(p2.getCategory()).build();

        searchRepository.saveAll(List.of(doc1, doc2));

        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    ResponseEntity<String> res = restTemplate.getForEntity(
                            getBaseUrl() + "/search?keyword=Apple", String.class);
                    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(res.getBody()).contains("Apple iPhone");
                });

        ResponseEntity<String> response = restTemplate.getForEntity(
                getBaseUrl() + "/search?keyword=Phone", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Apple iPhone");
        assertThat(response.getBody()).contains("Banana Phone");
    }

    private static void createTopicIfNotExists(String topic, String bootstrapServers) {
        Properties config = new Properties();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        try (AdminClient admin = AdminClient.create(config)) {
            Set<String> existingTopics = admin.listTopics().names().get(3, TimeUnit.SECONDS);
            if (!existingTopics.contains(topic)) {
                try {
                    admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1)))
                            .all().get(3, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof org.apache.kafka.common.errors.TopicExistsException) {
                        System.out.println("Topic already exists: " + topic);
                    } else {
                        throw e;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to check or create topic: " + topic, e);
        }
    }
}
