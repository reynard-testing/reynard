import yaml
import argparse

# A utility script to convert a given docker-compose.yaml file
# to add the proxies and auxiliary services required for FIT testing


def get_args():
    arg_parser = argparse.ArgumentParser()
    arg_parser.add_argument('yaml_file', type=str,
                            help='Path to the YAML file')

    arg_parser.add_argument('--filibuster',
                            action='store_true',
                            help='Patch filibuster build config')

    args = arg_parser.parse_args()
    return args


ADDITIONAL_ENV = [
    ["FLASK_DEBUG", None],
]

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
]


class ServiceBuilder:
    def __init__(self):
        self.service = {}
        pass

    def with_image(self, image: str):
        self.service['image'] = image
        return self

    def with_command(self, command: str):
        self.service['command'] = command
        return self

    def with_hostname(self, hostname: str):
        self.service['hostname'] = hostname
        return self

    def with_build(self, build: dict):
        self.service['build'] = build
        return self

    def with_ports(self, host: int, container: int):
        if 'ports' not in self.service:
            self.service['ports'] = []
        self.service['ports'].append(f'{host}:{container}')
        return self

    def with_environment(self, key: str, value: str):
        if 'environment' not in self.service:
            self.service['environment'] = []
        if value is None:
            self.service['environment'].append(f'{key}')
        else:
            self.service['environment'].append(f'{key}={value}')
        return self

    def with_volume(self, host: str, container: str):
        if 'volumes' not in self.service:
            self.service['volumes'] = []
        self.service['volumes'].append(f'{host}:{container}')
        return self

    def build(self):
        return self.service


class Converter:
    def __init__(self, data, filibuster_project: str):
        self.patch_filibuster = filibuster_project is not None
        self.filibuster_project = filibuster_project

        self.service_names = {
            'jaeger': 'jaeger',
            'controller': 'controller',
        }
        self.public_ports = {
            'jaeger': 16686,
            'controller': 6050,
        }
        self.controller_port = 8050
        self.data = data
        self.output = {
            "services": {},
        }
        self.proxy_list: list[str] = []

    def add_service(self, service_name: str, service_def: dict):
        self.output['services'][service_name] = service_def

    def convert_service(self, service_name: str, service_def: dict):
        proxy_service = ServiceBuilder() \
            .with_image(IMAGES['proxy']) \
            .with_environment('CONTROLLER_HOST', self.service_names['controller'] + ":5000") \
            .with_environment('CONTROL_PORT', self.controller_port) \
            .with_environment('SERVICE_NAME', service_name)

        hostname = service_def.get('hostname', service_name)
        proxy_service_name = f'{service_name}-proxy'
        real_service_name = f'{service_name}-real'

        real_service = ServiceBuilder()

        container_port = None
        host_port = None
        if self.patch_filibuster:
            real_service.with_build({
                "context": '../',
                "dockerfile": './Dockerfile.python.service',
                "args": {
                    "benchmark": self.filibuster_project,
                    "service": service_name,
                }
            })
        else:
            if 'image' in service_def:
                real_service.with_image(service_def['image'])
            if 'build' in service_def:
                real_service.with_build(service_def['build'])
        if 'ports' in service_def:
            for port in service_def['ports']:
                host_port, container_port = port.split(':')

        proxy_service.with_hostname(hostname)
        real_service.with_hostname(real_service_name)

        proxy_service.with_ports(host_port, container_port)
        proxy_service.with_environment(
            'PROXY_HOST', "0.0.0.0:" + container_port)
        proxy_service.with_environment(
            'PROXY_TARGET', "http://" + real_service_name + ":" + container_port)

        real_service.with_environment("OTEL_SERVICE_NAME", service_name)\
            .with_environment("OTEL_TRACES_EXPORTER", "otlp")\
            .with_environment("OTEL_EXPORTER_OTLP_ENDPOINT", "http://"+self.service_names['jaeger']+":4317")\
            .with_environment("OTEL_LOGS_EXPORTER", "none")\
            .with_environment("OTEL_METRICS_EXPORTER", "none")

        for env in ADDITIONAL_ENV:
            real_service.with_environment(env[0], env[1])

        self.proxy_list.append(hostname +
                               ":" + str(self.controller_port))
        self.add_service(proxy_service_name, proxy_service.build())
        self.add_service(real_service_name, real_service.build())

    def add_jaeger(self):
        service = ServiceBuilder() \
            .with_image(IMAGES['jaeger']) \
            .with_ports(self.public_ports['jaeger'], 16686) \
            .with_environment('COLLECTOR_OTLP_ENABLED', 'true') \
            .build()
        service_name = self.service_names['jaeger']
        self.add_service(service_name, service)

    def add_controller(self):
        service = ServiceBuilder() \
            .with_image(IMAGES['controller']) \
            .with_ports(self.public_ports['controller'], 5000) \
            .with_environment('PROXY_LIST', ",".join(self.proxy_list)) \
            .with_environment('FLASK_DEBUG', None) \
            .build()
        service_name = self.service_names['controller']
        self.add_service(service_name, service)

    def add_extra_services(self):
        self.add_jaeger()
        self.add_controller()

    def convert(self):
        services = self.data['services']
        for service_name, service_def in services.items():
            self.convert_service(service_name, service_def)

        self.add_extra_services()


if __name__ == '__main__':
    args = get_args()
    with open(args.yaml_file, 'r') as file:
        data = yaml.safe_load(file)

    filibuster_project = None
    if args.filibuster:
        filibuster_project = args.yaml_file.split('/')[-2]

    if filibuster_project is not None:
        if filibuster_project == 'netflix':
            ADDITIONAL_ENV.append(["NETFLIX_FAULTS", None])
            ADDITIONAL_ENV.append(["CHECK_TIMEOUTS", None])
        if filibuster_project == 'audible':
            ADDITIONAL_ENV.append(["BAD_METADATA", None])
        if filibuster_project == 'mailchimp':
            ADDITIONAL_ENV.append(["DB_READ_ONLY", None])

    converter = Converter(data, filibuster_project)
    converter.convert()

    file_extension = args.yaml_file.split('.')[-1]
    file_name = '.'.join(args.yaml_file.split('.')[:-1])
    new_file_name = f'{file_name}.fit.{file_extension}'
    with open(new_file_name, 'w') as file:
        yaml.dump(converter.output, file, sort_keys=False)
