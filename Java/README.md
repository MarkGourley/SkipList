# SkipList: Optimized Doubly-Linked List

A custom doubly-linked list implementation with O(√n) average-case access time through a skip-list inspired fast-access layer.

Available in both **Java** and **Python** with identical functionality and performance characteristics.

## Overview

Traditional linked lists have O(n) access time, making random access operations slow. This implementation adds a sparse "fast layer" above the main linked list, enabling much faster access to elements at arbitrary positions while maintaining the benefits of linked list insertion/deletion.

## Performance Characteristics

| Operation | Complexity | Notes |
|-----------|------------|-------|
| `add(value)` | O(1) amortized | Optimized tail operations with gap tracking |
| `insert(index, value)` | O(√n) average | Uses fast layer for positioning |
| `remove(index)` | O(√n) average | Uses fast layer for positioning |
| `remove(value)` | O(n) worst case | Optimized with bidirectional chunk-based search |
| `get(index)` | O(√n) average | Bidirectional search with fast layer |

## Key Features

- **Two-Layer Architecture**: Main doubly-linked list + sparse fast-access layer
- **Gap-Based Indexing**: Fast layer uses relative gaps between nodes instead of absolute indices
- **Dynamic Skip Distance**: Adjusts spacing between fast nodes based on list size (~√n)
- **Adaptive Rebalancing**: Maintains optimal fast layer density automatically
- **Bidirectional Search**: Chooses optimal direction (forward/backward) for access operations

## Repository Structure

```
skiplist/
├── README.md           # This file
├── java/
│   ├── README.md       # Java-specific documentation
│   └── SkipList/
│       └── SkipList.java
└── python/
    ├── README.md       # Python-specific documentation
    ├── skiplist/
    │   ├── __init__.py
    │   └── skiplist.py
    └── setup.py
```

## Quick Start

### Java
```java
import SkipList.SkipList;

SkipList<Integer> list = new SkipList<>();
list.add(42);
list.add(0, 99);  // Insert at index 0
Integer value = list.remove(0);
```

See [java/README.md](java/README.md) for full documentation.

### Python
```python
from skiplist import SkipList

lst = SkipList()
lst.add(42)
lst.insert(0, 99)  # Insert at index 0
value = lst.remove_at(0)
```

See [python/README.md](python/README.md) for full documentation.

## Why This Approach?

This implementation originated from frustration with traditional linked lists' poor mid-list access performance:

- **vs. Standard Linked Lists**: Much faster random access (O(√n) vs O(n))
- **vs. Dynamic Arrays**: Better insertion/deletion in middle (O(√n) vs O(n))
- **vs. Full Skip Lists**: Simpler, more predictable, no probabilistic behavior

Perfect for scenarios requiring both frequent random access and mid-list modifications.

## Design Philosophy

This is **not** a traditional skip list (which uses probabilistic multi-level structures for O(log n) access). Instead, it uses a deterministic single-level fast layer for:

- Predictable performance (no probabilistic behavior)
- Simpler implementation and maintenance
- Lower memory overhead
- Still provides significant improvement over O(n)

The skip distance grows proportionally to √n, balancing fast layer traversal with fine-grained access.

## Origin

Originally implemented in Java to solve real-world performance issues with mid-list access patterns. Later ported to Python to explore the same algorithmic approach across different languages while preserving identical behavior.

## License

MIT License - feel free to use and modify as needed.

## Contributing

This is a personal learning project exploring data structure optimization across languages. Suggestions and improvements are welcome! Open an issue or submit a pull request.

---

# java/README.md

# SkipList - Java Implementation

Fast doubly-linked list with O(√n) average-case access time. Implements `java.util.List<E>`.

## Installation

Add `SkipList.java` to your project:

```bash
src/
└── SkipList/
    └── SkipList.java
```

## Usage

```java
import SkipList.SkipList;

public class Example {
    public static void main(String[] args) {
        // Create a new list
        SkipList<Integer> list = new SkipList<>();
        
        // Append elements (O(1) amortized)
        for (int i = 0; i < 1000; i++) {
            list.add(i);
        }
        
        // Insert at position (O(√n) average)
        list.add(250, 999);
        
        // Remove by index (O(√n) average)
        Integer removed = list.remove(250);
        
        // Remove by value (O(n) worst case, optimized)
        boolean found = list.remove(Integer.valueOf(42));
        
        // Get size
        int size = list.size();
        
        // Clear all elements
        list.clear();
    }
}
```

## API

Implements core `java.util.List<E>` methods:

- `boolean add(E element)` - Append to end, O(1) amortized
- `void add(int index, E element)` - Insert at position, O(√n) average
- `E remove(int index)` - Remove by index, O(√n) average
- `boolean remove(Object o)` - Remove by value, O(n) worst case
- `int size()` - Get list size, O(1)
- `void clear()` - Remove all elements, O(1)

### Not Implemented

The following methods throw `UnsupportedOperationException`:
- `isEmpty()`, `contains()`, `iterator()`
- `toArray()` variants
- `get()`, `set()`, `indexOf()`, `lastIndexOf()`
- Collection bulk operations
- `listIterator()`, `subList()`

These can be added if needed for your use case.

## Implementation Details

### Architecture

Two linked structures:
1. **ListNode**: Standard doubly-linked list with data
2. **FastNode**: Sparse layer with "gaps" to main list nodes

### Key Constants

```java
MIN_SKIP = 25              // Minimum distance between fast nodes
REBALANCE_THRESHOLD = 100  // Operations before considering rebalance
SKIP_GROWTH_FACTOR = 1.5   // How skip distance grows with size
```

### Rebalancing

Automatically triggered when:
- 100 operations have occurred since last rebalance
- Fast layer density drops below √n / 2

### Memory Overhead

- Each `ListNode`: 3 references (prev, next, fastLink) + data
- Each `FastNode`: 4 references (target, prev, next) + 1 int (gap)
- Fast layer has ~√n nodes for a list of size n

## Java-Specific Features

- **Generic Type Support**: Works with any object type
- **List Interface**: Drop-in replacement where core methods suffice
- **Null Handling**: Properly handles null elements in comparisons
- **Exception Safety**: Throws `IndexOutOfBoundsException`, `IllegalStateException` appropriately

## Performance Notes

Best suited for:
- ✓ Frequent insertions/deletions anywhere in list
- ✓ Mix of sequential and random access patterns
- ✓ Lists of moderate to large size (>100 elements)

Not ideal for:
- ✗ Pure sequential access (use LinkedList)
- ✗ Pure random access (use ArrayList)
- ✗ Very small lists (<50 elements, overhead not worth it)

## Building

Compile with Java 8 or later:

```bash
javac SkipList/SkipList.java
```

Run tests/examples:

```bash
javac Example.java
java Example
```