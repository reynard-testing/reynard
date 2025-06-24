
from dataclasses import dataclass


@dataclass
class PortMapping:
    host: int
    container: int


@dataclass
class ServiceDefinition:
    service_name: str
    definition: dict
    image: str = None
    build: dict = None
    ports: list[PortMapping] = None
    environment: dict = None
    hostname: str = None


def parse_port(port_str: str) -> PortMapping:
    """
    Parse a port mapping string of the form 'host:container' and return a PortMapping object.
    """
    # we only support host or host:container format
    if "[" in port_str or "-" in port_str:
        raise ValueError(
            f"Invalid port format: {port_str}. Only 'host:container' or 'host' is supported.")

    if ':' not in port_str:
        return PortMapping(host=None, container=int(port_str))

    host_port, container_port = map(int, port_str.split(':'))
    return PortMapping(host=host_port, container=container_port)


def get_hostname(service_name: str, service_def: dict) -> str:
    """
    Get the hostname for a service, defaulting to the service name if not specified.
    """
    if 'hostname' in service_def:
        return service_def['hostname']
    # Default to service name if hostname is not specified
    return service_name


def parse_service_definition(service_name: str, service_def: dict) -> ServiceDefinition:
    return ServiceDefinition(
        service_name=service_name,
        definition=service_def,
        hostname=get_hostname(service_name, service_def),
    )
