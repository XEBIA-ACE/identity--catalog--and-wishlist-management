import unittest
from typing import get_type_hints

from math_ops import subtract


# Acceptance criterion: a106edcf-5452-46b5-81ea-aecef0b85948
class SubtractTests(unittest.TestCase):
    def test_subtract_returns_difference(self) -> None:
        cases = (
            (7, 3, 4),
            (3, 7, -4),
            (-7, -3, -4),
            (7, -3, 10),
            (-7, 3, -10),
            (7, 0, 7),
            (0, 7, -7),
            (0, 0, 0),
            (7, 7, 0),
        )
        for a, b, expected in cases:
            with self.subTest(a=a, b=b):
                self.assertEqual(subtract(a, b), expected)

    def test_subtract_has_integer_type_hints(self) -> None:
        self.assertEqual(get_type_hints(subtract), {"a": int, "b": int, "return": int})


if __name__ == "__main__":
    unittest.main()
