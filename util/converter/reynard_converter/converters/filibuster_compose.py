
from reynard_converter.converters.docker_compose import ComposeConverter


class FilibusterConverter(ComposeConverter):
    """
    FilibusterConverter extends the ComposeConverter to handle specific requirements for Filibuster projects.
    It adds additional environment variables and modifies service definitions as needed.
    """

    def __init__(self, data, filibuster_project: str):
        super().__init__(data)
        self.filibuster_project = filibuster_project
        self.set_additional_env()

    def set_additional_env(self):
        self.additional_envs = [
            ["FLASK_DEBUG", None],
        ]

        if self.filibuster_project == 'netflix':
            self.additional_envs.append(["NETFLIX_FAULTS", None])
            self.additional_envs.append(["CHECK_TIMEOUTS", None])
        elif self.filibuster_project == 'audible':
            self.additional_envs.append(["BAD_METADATA", None])
        elif self.filibuster_project == 'mailchimp':
            self.additional_envs.append(["DB_READ_ONLY", None])

    def convert_service(self, service):
        for env in self.additional_envs:
            real_service.with_environment(env[0], env[1])

        real_service.with_build({
            "context": '../',
            "dockerfile": './Dockerfile.python.service',
            "args": {
                "benchmark": self.filibuster_project,
                "service": service_name,
            }
        })
        return super().convert_service(service)
