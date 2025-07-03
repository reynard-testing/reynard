
class ComposeBuilder:
    """ A class to build a Docker Compose definition. """

    def __init__(self):
        self.definition = {
            "services": {},
        }

    # Add a top-level key-value pair to the Docker Compose definition.
    def add_key_value(self, key: str, value: str):
        if key == 'services':
            raise ValueError("Use add_service_definition to add services")

        if key == 'version':
            # Docker compose version is obsolete in newer versions
            return

        if key not in self.definition:
            self.definition[key] = {}

        self.definition[key] = value

    def add_service_definition(self, service_name: str, service_def: dict):
        """ Add a service definition to the Docker Compose definition. """
        self.definition['services'][service_name] = service_def

    def get_definition(self) -> dict:
        """ Returns the complete Docker Compose definition."""
        return self.definition


class ServiceNameManager:
    """ A class to manage service names in a Docker Compose definition, ensuring uniqueness. """

    def __init__(self):
        self.taken_names = set()

    def add_name(self, name: str):
        """ Add a service name to the set of taken names. """
        if name in self.taken_names:
            raise ValueError(f"Service name '{name}' is already taken.")

        self.taken_names.add(name)

    def get_safe_name(self, name: str) -> str:
        """ Generate a unique service name by appending a number if necessary. """

        counter = 1
        safe_name = name

        # Check if the name is already taken, and append a counter if it is
        while safe_name in self.taken_names:
            safe_name = f"{name}-{counter}"
            counter += 1

        self.taken_names.add(safe_name)

        return safe_name


class PortManager:
    """ A class to manage ports in a Docker Compose definition, ensuring uniqueness. """

    def __init__(self):
        self.taken_ports = set()

    def add_port(self, port: int):
        """ Add a port to the set of taken ports."""
        if port in self.taken_ports:
            raise ValueError(f"Port {port} is already taken.")

        self.taken_ports.add(port)

    def get_available_port(self, start_port: int) -> int:
        """ Find the nearest available port starting from the given port. """

        # Find the next available port starting from start_port
        while start_port in self.taken_ports:
            start_port += 1

        self.taken_ports.add(start_port)

        return start_port
