
class ServiceBuilder:
    """
    ServiceBuilder is a builder class for constructing Docker Compose service definitions as Python dictionaries.
    """

    def __init__(self, base_def: dict = None):
        self.service = base_def if base_def else {}

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

    def build(self) -> dict:
        return self.service
