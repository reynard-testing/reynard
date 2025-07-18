
IMAGES = {
    'jaeger': 'jaegertracing/jaeger:latest',
    'controller': '${CONTROLLER_IMAGE:-dflipse/reynard-controller:latest}',
    'proxy': '${PROXY_IMAGE:-dflipse/reynard-proxy:latest}',
}

IGNORED_IMAGES = [
    'postgres',
    'mysql',
    'mariadb',
    'redis',
    'mongo',
    'cassandra',
    'elasticsearch',
    'couchbase',
    'neo4j',
    'influxdb',
    'clickhouse',
    'sqlite',
    'arangodb',
    'dynamodb',
    'scylla',
    'h2',
    'hsql',
    'kafka',
    'rabbitmq',
    'zookeeper',
    'nginx',
    'apache',
    'httpd',
    'traefik',
    'haproxy',
    'caddy',
    'lighttpd',
    'envoyproxy',
    'istio',
    'hashicorp/consul',
    'memcached',
    'jaegertracing',
]
