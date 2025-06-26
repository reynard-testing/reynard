
from dataclasses import dataclass

from reynard_converter.config import IGNORED_IMAGES


@dataclass
class PortMapping:
    host: int
    container: int


@dataclass
class PortMapping:
    host: int
    container: int


@dataclass
class ServiceDefinition:
    definition: dict
    service_name: str
    ignored: bool
    hostname: str
    ports: list[PortMapping]
    container_port: int | None = None


def parse_port(port_str: str, service_name: str, hints: dict) -> PortMapping:
    """
    Parse a port mapping string of the form 'host:container' and return a PortMapping object.
    """
    # we only support host or host:container format
    if "[" in port_str or "-" in port_str or "/" in port_str:
        return None

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


def should_not_be_instrumented(image_name: str) -> bool:
    for prefix in IGNORED_IMAGES:
        if image_name.startswith(prefix):
            return True


def get_hint_port(hints: dict, service_name: str) -> int | None:
    """
    Get the port hint for a service from the hints dictionary.
    """
    hint_key = f'{service_name}_port'
    if hint_key in hints:
        host_port = hints[hint_key]
        if isinstance(host_port, int):
            return host_port
        else:
            raise ValueError(
                f"Hint for {hint_key} must be an integer, got {type(host_port).__name__}")
    return None


def parse_service_definition(service_name: str, service_def: dict, hints: dict) -> ServiceDefinition:
    image_name = service_def.get('image', '')
    should_ignore = should_not_be_instrumented(image_name)

    ports_def = service_def.get('ports', [])
    container_port = None
    defined_ports = [parse_port(port, service_name, hints)
                     for port in ports_def if port]
    defined_ports = [x for x in defined_ports if x is not None]

    if not should_ignore:
        # if the service is ignored, we don't parse ports (db's are more likely to have weird port mappings)

        if defined_ports and len(defined_ports) == 1:
            container_port = defined_ports[0].container
        else:
            container_port = get_hint_port(hints, service_name)

    return ServiceDefinition(
        service_name=service_name,
        definition=service_def,
        ignored=should_ignore,
        hostname=get_hostname(service_name, service_def),
        container_port=container_port,
        ports=defined_ports,
    )
