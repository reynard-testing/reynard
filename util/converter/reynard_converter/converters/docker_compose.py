
import yaml

from reynard_converter.config import IGNORED_IMAGES, IMAGES
from reynard_converter.docker_compose.service_parser import ServiceDefinition, parse_service_definition
from reynard_converter.docker_compose.service_builder import ServiceBuilder


class ComposeConverter:
    def __init__(self, output_path: str, input: dict = None):
        self.output_path = output_path

        self.input = input if input is not None else {
            "services": {},
        }
        self.output = {
            "services": {},
        }

        self.services: list[ServiceDefinition] = []

        service_keys = self.input.get('services', {}).keys()
        for service_name in service_keys:
            service_def = self.input['services'][service_name]
            self.services.append(
                parse_service_definition(service_name, service_def))

        taken_names = set()
        taken_ports = set()
        for service in self.services:
            taken_names.add(service.service_name)
            if service.ports:
                for mapping in service.ports:
                    if mapping.host is not None:
                        taken_ports.add(mapping.host)

        self.service_names = {
            'jaeger': self.get_nearest_available_name('reynard-jaeger', taken_names),
            'controller': self.get_nearest_available_name('reynard-controller', taken_names),
        }

        self.public_ports = {
            'jaeger': self.get_nearest_available_port(16686, taken_ports),
            'controller': self.get_nearest_available_port(6050, taken_ports),
        }

        self.controller_port = 5000
        self.proxy_list: list[str] = []

    def get_nearest_available_port(self, port: int, taken_ports: set[int]) -> int:
        """
        Find the nearest available port starting from the given port.
        """
        while port in taken_ports:
            port += 1
        return port

    def get_nearest_available_name(self, name: str, taken_names: set[str]) -> str:
        """
        Find the nearest available name by appending a number if necessary.
        """
        if name not in taken_names:
            return name

        i = 1
        new_name = f"{name}-{i}"
        while new_name in taken_names:
            i += 1
            new_name = f"{name}-{i}"
        return new_name

    def add_service_definition(self, service_name: str, service_def: dict):
        self.output['services'][service_name] = service_def

    def add_jaeger(self):
        service = ServiceBuilder() \
            .with_image(IMAGES['jaeger']) \
            .with_ports(self.public_ports['jaeger'], 16686) \
            .with_environment('COLLECTOR_OTLP_ENABLED', 'true') \
            .build()
        service_name = self.service_names['jaeger']
        self.add_service_definition(service_name, service)

    def add_controller(self):
        service = ServiceBuilder() \
            .with_image(IMAGES['controller']) \
            .with_ports(self.public_ports['controller'], 5000) \
            .with_environment('PROXY_LIST', ",".join(self.proxy_list)) \
            .with_environment('FLASK_DEBUG', None) \
            .build()
        service_name = self.service_names['controller']
        self.add_service_definition(service_name, service)

    def add_extra_services(self):
        self.add_jaeger()
        self.add_controller()

    def should_not_be_instrumented(self, service: ServiceDefinition) -> bool:
        image_name = service.definition.get('image', '')
        for prefix in IGNORED_IMAGES:
            if image_name.startswith(prefix):
                return True
        return False

    def convert_service(self, service: ServiceDefinition):
        if self.should_not_be_instrumented(service):
            self.add_service_definition(
                service.service_name, service.definition)
            return

        real_service = ServiceBuilder(service.definition)
        new_service_hostname = f'{service.hostname}-instrumented'
        new_service_name = f'{service.hostname}-instrumented'
        proxy_service_name = f'{service.hostname}-proxy'

        container_port = None
        host_port = None
        proxy_control_port = None

        proxy_service = ServiceBuilder() \
            .with_image(IMAGES['proxy']) \
            .with_environment('CONTROLLER_HOST', f"{self.service_names['controller']}:{self.controller_port}") \
            .with_environment('CONTROL_PORT', proxy_control_port) \
            .with_environment('SERVICE_NAME', service.service_name) \
            .with_environment('PROXY_HOST', f"0.0.0.0:{container_port}") \
            .with_environment('PROXY_TARGET', f"http://{new_service_hostname}:{container_port}") \
            .with_ports(host_port, container_port)

        # swap the hostnames, so the proxy is the one that gets the real hostname
        proxy_service.with_hostname(service.hostname)
        real_service.with_hostname(new_service_hostname)

        # Add OTEL environment variables to the real service
        real_service.with_environment("OTEL_SERVICE_NAME", service.service_name)\
            .with_environment("OTEL_TRACES_EXPORTER", "otlp")\
            .with_environment("OTEL_EXPORTER_OTLP_ENDPOINT", f"http://{self.service_names['jaeger']}:4317")\
            .with_environment("OTEL_LOGS_EXPORTER", "none")\
            .with_environment("OTEL_METRICS_EXPORTER", "none")

        self.proxy_list.append(service.hostname + ":" +
                               str(proxy_control_port))
        self.add_service_definition(proxy_service_name, proxy_service.build())
        self.add_service_definition(new_service_name, real_service.build())

        # if 'depends_on' in service.definition:

    def convert(self):
        for service in self.services:
            self.convert_service(service)

        self.add_extra_services()
        self.output_file()

    def output_file(self):
        """
        Write the converted Docker Compose configuration to a file.
        """
        with open(self.output_path, 'w') as f:
            yaml.dump(self.output, f, default_flow_style=False, sort_keys=False)
