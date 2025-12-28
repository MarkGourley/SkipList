# SkipList - Python Implementation

Fast doubly-linked list with O(√n) average-case access time. Direct port of the Java version.

## Installation

### From Source

```bash
git clone https://github.com/yourusername/skiplist.git
cd skiplist/python
pip install -e .
```

### Package Structure

```
python/
├── skiplist/
│   ├── __init__.py
│   └── skiplist.py
└── setup.py
```

## Usage

```python
from skiplist import SkipList

# Create a new list
lst = SkipList()

# Append elements (O(1) amortized)
for i in range(1000):
    lst.add(i)

# Access elements (O(√n) average)
value = lst.get(500)

# Insert at position (O(√n) average)
lst.insert(250, 999)

# Remove by index (O(√n) average)
removed = lst.remove_at(250)

# Remove by value (O(n) worst case, optimized)
found = lst.remove(42)  # Returns True if found and removed

# Get size
size = len(lst)

# Clear all elements
lst.clear()
```

## API

- `add(value)` - Append to end, O(1) amortized
- `insert(index, value)` - Insert at position, O(√n) average
- `remove_at(index)` - Remove by index, O(√n) average, returns value
- `remove(value)` - Remove by value, O(n) worst case, returns bool
- `get(index)` - Access by index, O(√n) average
- `get_node(index)` - Internal: get node at index
- `clear()` - Remove all elements, O(1)
- `__len__()` - Get size, O(1)

## Implementation Details

### Architecture

Two linked structures:
1. **ListNode**: Standard doubly-linked list with data
2. **FastNode**: Sparse layer with "gaps" to main list nodes

### Key Constants

```python
MIN_SKIP = 25              # Minimum distance between fast nodes
REBALANCE_THRESHOLD = 100  # Operations before considering rebalance
SKIP_GROWTH_FACTOR = 1.5   # How skip distance grows with size
```

### Memory Efficiency

Uses `__slots__` in both node classes to reduce memory overhead:

```python
class ListNode:
    __slots__ = ("data", "prev", "next", "fast")

class FastNode:
    __slots__ = ("target", "prev", "next", "gap")
```

### Rebalancing

Automatically triggered when:
- 100 operations have occurred since last rebalance
- Fast layer density drops below √n / 2

## Python-Specific Features

- **Pythonic API**: Uses `len()`, `insert()`, `remove()` naming conventions
- **Memory Efficient**: `__slots__` for reduced memory footprint
- **Exception Handling**: Raises `IndexError` for out-of-bounds access
- **Type Flexible**: Works with any hashable/comparable types

## Performance Notes

Best suited for:
- ✓ Frequent insertions/deletions anywhere in list
- ✓ Mix of sequential and random access patterns
- ✓ Lists of moderate to large size (>100 elements)

Not ideal for:
- ✗ Pure sequential access (use collections.deque)
- ✗ Pure random access (use built-in list)
- ✗ Very small lists (<50 elements, overhead not worth it)

## Development

### Running Tests

```python
# Simple smoke test
from skiplist import SkipList

lst = SkipList()
for i in range(100):
    lst.add(i)

assert len(lst) == 100
assert lst.get(50) == 50
lst.insert(50, 999)
assert lst.get(50) == 999
assert lst.remove(999) == True
assert len(lst) == 100
```

### Requirements

- Python 3.6+ (uses f-strings in error messages)
- No external dependencies

## Differences from Java Version

- Method names follow Python conventions (`remove_at` vs `remove(int)`)
- Uses `__len__()` instead of `size()`
- Returns `None` for void methods instead of explicit return statements
- Error messages use f-strings for clarity
- Uses `None` instead of `null`

Algorithmic behavior is identical across both implementations.