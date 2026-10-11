import unittest

from reynard_converter.docker_compose.service_builder import (
    ServiceBuilder, parse_port)


class TestParsePort(unittest.TestCase):
    def test_container_only(self):
        self.assertEqual(parse_port("80"), parse_port("80"))
        mapping = parse_port("80")
        self.assertIsNone(mapping.host)
        self.assertEqual(mapping.container, 80)

    def test_host_container(self):
        mapping = parse_port("8080:80")
        self.assertEqual(mapping.host, 8080)
        self.assertEqual(mapping.container, 80)

    def test_ip_host_container(self):
        mapping = parse_port("127.0.0.1:8080:80")
        self.assertEqual(mapping.host, 8080)
        self.assertEqual(mapping.container, 80)

    def test_rejects_range(self):
        self.assertIsNone(parse_port("8080-8090:80"))


class TestWithEnvironment(unittest.TestCase):
    def test_normalizes_dict_form(self):
        builder = ServiceBuilder("svc", {"environment": {"FOO": "bar"}})
        builder.with_environment("BAZ", "qux")
        self.assertIn("FOO=bar", builder.definition["environment"])
        self.assertIn("BAZ=qux", builder.definition["environment"])


if __name__ == "__main__":
    unittest.main()
