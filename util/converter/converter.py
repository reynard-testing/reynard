
import yaml
import argparse

from reynard_converter.converters.docker_compose import ComposeConverter
from reynard_converter.converters.filibuster_compose import FilibusterConverter

# A utility script to convert a given docker-compose.yaml file
# to add the proxies and auxiliary services required for FIT testing


def as_output_file_name(yaml_file: str) -> str:
    """
    Generate an output file name based on the input YAML file name.
    The output file will have the same base name but with a '.fit' suffix before the file extension.
    """
    file_extension = yaml_file.split('.')[-1]
    file_name = '.'.join(yaml_file.split('.')[:-1])
    return f'{file_name}.fit.{file_extension}'


def convert_filibuster(yaml_file: str, filibuster_project: str):
    with open(yaml_file, 'r') as file:
        data = yaml.safe_load(file)
        output_file = as_output_file_name(yaml_file)
        converter = FilibusterConverter(output_file, data, filibuster_project)
        converter.convert()


def convert_compose(yaml_file: str, hints_file: str):
    hints = {}
    if hints_file:
        with open(hints_file, 'r') as file:
            if hints_file.endswith('.yaml') or hints_file.endswith('.yml'):
                hints = yaml.safe_load(file)
            elif hints_file.endswith('.json'):
                import json
                hints = json.load(file)
            else:
                raise ValueError("Hints file must be in YAML or JSON format")

    with open(yaml_file, 'r') as file:
        data = yaml.safe_load(file)
        output_file = as_output_file_name(yaml_file)
        converter = ComposeConverter(output_file, data, hints=hints)
        converter.convert()


if __name__ == '__main__':
    arg_parser = argparse.ArgumentParser()

    arg_parser.add_argument('converter',
                            type=str,
                            choices=['filibuster', 'compose'],
                            help='Type of converter to use')

    # Parse known args to check converter type
    args, _ = arg_parser.parse_known_args()

    # If converter is filibuster, add --project flag
    if args.converter == 'filibuster':
        arg_parser.add_argument('yaml_file', type=str,
                                help='Path to the YAML file')
        arg_parser.add_argument('--project', type=str, required=False,
                                help='Project name for filibuster converter')

        # Re-parse with the new argument
        args = arg_parser.parse_args()
        convert_filibuster(args.yaml_file, args.project)
    elif args.converter == 'compose':
        arg_parser.add_argument('yaml_file', type=str,
                                help='Path to the Docker Compose YAML file')

        arg_parser.add_argument('--hints-file', type=str, required=False,
                                help='Path to a yaml or json file with hints for the converter')
        # Re-parse with the new argument
        args = arg_parser.parse_args()
        convert_compose(args.yaml_file, args.hints_file)
