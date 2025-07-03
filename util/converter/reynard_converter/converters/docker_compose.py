
import yaml
from reynard_converter.config import IGNORED_IMAGES, IMAGES
from reynard_converter.docker_compose.compose_builder import (
    ComposeBuilder, PortManager, ServiceNameManager)
from reynard_converter.docker_compose.service_builder import ServiceBuilder


class ComposeConverter:
    """
    ComposeConverter is a class that converts a Docker Compose file to automatically add instrumentation
    It modifies the service definitions to add proxies, instrumented services,
    and auxiliary services like Jaeger and the instrumentation controller.

    Please not that this currently only supports Docker Compose files with these conditions:
    - The services' hostnames are either the service name or a custom hostname (not a network alias).
    """

    def __init__(self, output_path: str, input: dict = None, hints: dict = None):
        self.output_path = output_path

        self.input = input if input is not None else {
            "services": {},
        }

        # Hints is a dict that contains info required for the automatic conversion
        # but not part of the original Docker Compose file.
        self.hints = hints or {}

        # Parse existing services
        self.services: list[ServiceBuilder] = []
        for service_name in self.input.get('services', {}).keys():
            service_def = self.input['services'][service_name]
            self.services.append(ServiceBuilder(service_name, service_def))

        # Initialize managers for service names and ports
        # to ensure unique names and available ports
        self.name_manager = ServiceNameManager()
        self.port_manager = PortManager()
        for service in self.services:
            self.name_manager.add_name(service.name)
            for mapping in service.get_ports():
                if mapping.host is not None:
                    self.port_manager.add_port(mapping.host)

        # Define service names and public ports for Jaeger and Controller
        self.service_names = {
            'jaeger': self.name_manager.get_safe_name('reynard-jaeger'),
            'controller': self.name_manager.get_safe_name('reynard-controller'),
        }

        self.public_ports = {
            'jaeger': self.port_manager.get_available_port(16686),
            'controller': self.port_manager.get_available_port(6116),
        }

        # Configuration parameters
        self.controller_port = 5000
        self.proxy_control_port = 5115
        self.instrumented_suffix = '-instrumented'
        self.proxy_suffix = '-proxy'

        # List of proxies (service_name:control-port)
        self.proxy_list: list[str] = []

        # Define the passes to apply to the services
        # Each is a conversion from list[ServiceBuilder] -> list[ServiceBuilder]
        self.passes = [
            self._add_proxy_pass,
            self._patch_depends_on_pass,
        ]

    # Predicate whether a service should not be instrumented
    def _should_not_be_instrumented(self, service: ServiceBuilder) -> bool:
        image_name = service.get_image_name()
        if image_name:
            for prefix in IGNORED_IMAGES:
                if image_name.startswith(prefix):
                    return True
        return False

    # Get the container port, either from the service definition or from hints.
    def _get_container_port(self, service: ServiceBuilder) -> int | None:
        """
        Get the port hint for a service from the hints dictionary.
        """
        port = service.get_container_port()
        if port is not None:
            return port

        # If no port is defined in the service, check hints
        hint_key = f'{service.name}_port'
        if hint_key in self.hints:
            host_port = self.hints[hint_key]
            if isinstance(host_port, int):
                return host_port
            else:
                raise ValueError(
                    f"Hint for {hint_key} must be an integer, got {type(host_port).__name__}")
        return None

    # Apply name patching in the 'depends_on' field of a service.
    def _patch_depends_on(self, service: ServiceBuilder, patched_names: dict[str, str]) -> ServiceBuilder:
        if 'depends_on' not in service.definition:
            return service

        depends_on = service.definition['depends_on']

        if isinstance(depends_on, dict):
            # Patch keys in the dictionary
            patched_depends_on = {}
            for dep_name, dep_config in depends_on.items():
                patched_name = patched_names.get(dep_name, dep_name)
                patched_depends_on[patched_name] = dep_config
            service.definition['depends_on'] = patched_depends_on
        elif isinstance(depends_on, list):
            # Patch names in the list
            patched_depends_on = [patched_names.get(
                dep_name, dep_name) for dep_name in depends_on]
            service.definition['depends_on'] = patched_depends_on

        return service

    # the depends_on field might contain service names that are instrumented,
    # so we need to patch them to the instrumented service names.
    def _patch_depends_on_pass(self, services: list[ServiceBuilder]) -> list[ServiceBuilder]:
        # Create a mapping of original service names to instrumented names
        patched_names = {}

        # Collect all instrumented service names
        for service in services:
            if service.name.endswith(self.instrumented_suffix):
                # Remove '-instrumented'
                to_patch = service.name[:-len(self.instrumented_suffix)]
                patched_names[to_patch] = service.name

        # Patch the depends_on field for each service
        print(f"Patching: {patched_names}")
        return [self._patch_depends_on(service, patched_names) for service in services]

    # Add a proxy for each service that should be instrumented.
    def _add_proxy_pass(self, services: list[ServiceBuilder]) -> list[ServiceBuilder]:
        """
        Add a proxy to each service that is not already instrumented.
        """
        updated_services = []
        for service in services:
            if self._should_not_be_instrumented(service):
                updated_services.append(service)
                continue

            # Add proxy for the service
            proxy_services = self._add_proxy(service)
            updated_services.extend(proxy_services)

        return updated_services

    # Convert a service to an instrumented service and a proxy.
    def _add_proxy(self, service: ServiceBuilder) -> tuple[ServiceBuilder, ServiceBuilder]:
        print(f"Adding proxy for service: {service.name}")

        # Define the new names (hostname and service name)
        service_hostname = service.get_hostname()
        new_service_hostname = f'{service_hostname}{self.instrumented_suffix}'
        proxy_service_name = f'{service_hostname}{self.proxy_suffix}'

        # Create a copy of the service to instrument, and rename it
        # This is to avoid Compose resolving the hostname due the service name
        instrumented_service = service.copy()
        instrumented_service.name = f'{service.name}{self.instrumented_suffix}'

        # Determine service ports
        container_port = self._get_container_port(service)

        # Set the control port, making sure it does not conflict with the container port
        proxy_control_port = self.proxy_control_port
        if container_port is not None and container_port == self.proxy_control_port:
            proxy_control_port = self.proxy_control_port + 1

        # If the container port is not defined, use a placeholder
        # This is to ensure that the proxy can still be created
        if container_port is None:
            container_port = f"<!!!-port-of-{service.name}-!!!>"

        # Create the proxy service
        proxy_service = ServiceBuilder(proxy_service_name) \
            .with_image(IMAGES['proxy']) \
            .with_environment('CONTROLLER_HOST', f"{self.service_names['controller']}:{self.controller_port}") \
            .with_environment('CONTROL_PORT', proxy_control_port) \
            .with_environment('LOG_LEVEL', None) \
            .with_environment('SERVICE_NAME', service.name) \
            .with_environment('PROXY_HOST', f"0.0.0.0:{container_port}") \
            .with_environment('PROXY_TARGET', f"http://{new_service_hostname}:{container_port}") \

        # Check if the container port is exposed in the original service
        host_port = None
        for mapping in service.get_ports():
            if mapping.container == container_port:
                host_port = mapping.host
                break

        instrumented_service.clear_ports()

        if host_port is not None:
            proxy_service.with_ports(host_port, container_port)

        # swap the hostnames, so the proxy is the one that gets the real hostname
        proxy_service.with_hostname(service_hostname)
        instrumented_service.with_hostname(new_service_hostname)

        # Add OTEL environment variables to the real service
        instrumented_service.with_environment("OTEL_SERVICE_NAME", service.name)\
            .with_environment("OTEL_TRACES_EXPORTER", "otlp")\
            .with_environment("OTEL_EXPORTER_OTLP_ENDPOINT", f"http://{self.service_names['jaeger']}:4317")\
            .with_environment("OTEL_LOGS_EXPORTER", "none")\
            .with_environment("OTEL_METRICS_EXPORTER", "none")

        self.proxy_list.append(service_hostname + ":" +
                               str(proxy_control_port))

        return (proxy_service, instrumented_service)

    def _add_jaeger(self):
        service_name = self.service_names['jaeger']
        service = ServiceBuilder(service_name) \
            .with_image(IMAGES['jaeger']) \
            .with_ports(self.public_ports['jaeger'], 16686) \
            .with_environment('COLLECTOR_OTLP_ENABLED', 'true')
        self.services.append(service)

    def _add_controller(self):
        service_name = self.service_names['controller']
        service = ServiceBuilder(service_name) \
            .with_image(IMAGES['controller']) \
            .with_ports(self.public_ports['controller'], 5000) \
            .with_environment('PROXY_LIST', ",".join(self.proxy_list)) \
            .with_environment('LOG_LEVEL', None)
        self.services.append(service)

    def _add_extra_services(self):
        self._add_jaeger()
        self._add_controller()

    def convert(self):
        # Modify the existing services in multiple passes.
        for idx, service_pass in enumerate(self.passes, start=1):
            print(f"Pass {idx}: {service_pass.__name__}")
            services_copy = [service.copy() for service in self.services]
            self.services = service_pass(services_copy)

        # Add the extra services (Jaeger and Controller)
        self._add_extra_services()

        # Output the converted Docker Compose configuration to a file
        self._output_file()

    def _output_file(self):
        """
        Write the converted Docker Compose configuration to a file.
        """

        # Start building a compose file
        compose = ComposeBuilder()

        # Add all non-services keys from the input
        for key, value in self.input.items():
            if key != "services":
                compose.add_key_value(key, value)

        # Add all (converted) services to the compose file
        for service in self.services:
            compose.add_service_definition(service.name, service.definition)

        # output the compose file to the specified path
        print(f"Writing converted Docker Compose to {self.output_path}")
        with open(self.output_path, 'w') as out_file:
            yaml.dump(compose.get_definition(), out_file,
                      default_flow_style=False, sort_keys=False)
