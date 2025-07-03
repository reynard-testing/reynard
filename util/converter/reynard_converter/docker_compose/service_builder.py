from copy import deepcopy
from dataclasses import dataclass


@dataclass
class PortMapping:
    host: int
    container: int


@dataclass
class PortMapping:
    host: int
    container: int


def parse_port(port_str: str) -> PortMapping:
    """
    Parse a port mapping string in the format "host:container" or "container".
    """
    # we only support host or host:container format
    if "[" in port_str or "-" in port_str or "/" in port_str:
        return None

    if ':' not in port_str:
        return PortMapping(host=None, container=int(port_str))

    host_port, container_port = map(int, port_str.split(':'))
    return PortMapping(host=host_port, container=container_port)


class ServiceBuilder:
    """
    ServiceBuilder is a builder class for constructing Docker Compose service definitions.
    """

    def __init__(self, name: str, base_def: dict = None):
        self.name = name
        self.definition = base_def if base_def else {}

    def with_image(self, image: str):
        """ Sets the image for the service. """
        self.definition['image'] = image
        return self

    def with_command(self, command: str):
        """ Sets the command to run in the service container. """
        self.definition['command'] = command
        return self

    def with_hostname(self, hostname: str):
        """ Sets the hostname for the service. """
        self.definition['hostname'] = hostname
        return self

    def with_build(self, build: dict):
        """ Add a build configuration to the service definition."""
        self.definition['build'] = build
        return self

    def with_ports(self, host: int, container: int):
        """ Adds a port mapping to the service definition."""
        if 'ports' not in self.definition:
            self.definition['ports'] = []
        self.definition['ports'].append(f'{host}:{container}')
        return self

    def clear_ports(self):
        """ Clears the ports defined for the service. """
        self.definition.pop('ports', None)
        return self

    def with_environment(self, key: str, value: str):
        if 'environment' not in self.definition:
            self.definition['environment'] = []

        for env in self.definition['environment']:
            if env.startswith(f'{key}='):
                # If the key already exists, update its value
                self.definition['environment'].remove(env)
                break

        if value is None:
            self.definition['environment'].append(f'{key}')
        else:
            self.definition['environment'].append(f'{key}={value}')
        return self

    def with_volume(self, host: str, container: str):
        """ Adds a volume mapping to the service definition. """
        if 'volumes' not in self.definition:
            self.definition['volumes'] = []
        self.definition['volumes'].append(f'{host}:{container}')
        return self

    def get_hostname(self) -> str:
        """
        Returns the hostname of the service, defaulting to the service name if not specified.
        """
        if 'hostname' in self.definition:
            return self.definition['hostname']

        # Default to service name if hostname is not specified
        return self.name

    def get_image_name(self) -> str:
        """
        Returns the image name of the service.
        """
        return self.definition.get('image', '')

    def get_ports(self) -> list[PortMapping]:
        """
        Returns a list of PortMapping objects for the service.
        """
        ports = self.definition.get('ports', [])
        parsed_ports = [parse_port(port) for port in ports if port]
        return [port for port in parsed_ports if port is not None]

    def get_container_port(self) -> int | None:
        """
        Returns the container port if specified, otherwise returns None.
        """
        ports = self.get_ports()
        if len(ports) == 1:
            return ports[0].container

        return None

    def build(self) -> dict:
        return self.definition

    def copy(self) -> 'ServiceBuilder':
        """
        Returns a copy of the ServiceBuilder instance.
        """
        return ServiceBuilder(self.name, deepcopy(self.definition))
