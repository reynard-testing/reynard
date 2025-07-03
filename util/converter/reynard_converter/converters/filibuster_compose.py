
from reynard_converter.converters.docker_compose import ComposeConverter
from reynard_converter.docker_compose.service_builder import ServiceBuilder


class FilibusterConverter(ComposeConverter):
    """
    FilibusterConverter extends the ComposeConverter to handle specific requirements for Filibuster projects.
    It adds additional environment variables and modifies service definitions as needed.
    This only works when including the changes of https://github.com/delanoflipse/filibuster-corpus/tree/ds-fit-benchmark-changes
    """

    def __init__(self, filibuster_project: str, output_path: str, input: dict = None, hints: dict = None):
        super().__init__(
            output_path=output_path,
            input=input,
            hints=hints
        )
        self.filibuster_project = filibuster_project

        # These configurations are set be in line with the original converter
        self.instrumented_suffix = '-real'
        self.proxy_control_port = 8050
        self.public_ports['controller'] = self.port_manager.get_available_port(
            6050)
        self.service_names = {
            'jaeger': self.name_manager.get_safe_name('jaeger'),
            'controller': self.name_manager.get_safe_name('controller'),
        }

        # Add two new passes to the converter
        self.passes.insert(0, self.use_new_dockerfile_pass)
        self.passes.insert(0, self.patch_envs_pass)

    def patch_envs_pass(self, services: list[ServiceBuilder]) -> list[ServiceBuilder]:
        # Adding FLASK_DEBUG to (optionally) enable debugging the instrumented services
        # Note: None implies that the value is copied over, hence its configurarable
        additional_envs = [
            ["FLASK_DEBUG", None],
        ]

        # Add project-specific environment variables for faults.
        if self.filibuster_project == 'netflix':
            additional_envs.append(["NETFLIX_FAULTS", None])
            additional_envs.append(["CHECK_TIMEOUTS", None])
        elif self.filibuster_project == 'audible':
            additional_envs.append(["BAD_METADATA", None])
        elif self.filibuster_project == 'mailchimp':
            additional_envs.append(["DB_READ_ONLY", None])

        print(f"Adding additional environment variables: {additional_envs}")

        for service in services:
            # patch additional environment variables to original services
            for env in additional_envs:
                service.with_environment(env[0], env[1])

        return services

    def use_new_dockerfile_pass(self, services: list[ServiceBuilder]) -> list[ServiceBuilder]:
        for service in services:
            # Use a new build configuration for each service
            service.with_build({
                "context": '../',
                "dockerfile": './Dockerfile.python.service',
                "args": {
                    "benchmark": self.filibuster_project,
                    "service": service.name,
                }
            })

        return services
